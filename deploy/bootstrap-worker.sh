#!/usr/bin/env bash
set -Eeuo pipefail

# VibeCode worker bootstrap.
# Installs deterministic tooling only. It intentionally DOES NOT authenticate
# Claude, Codex, Grok, GitHub, Tailscale, or any other external account.
# Safe to re-run on Ubuntu/Debian VPSes and cloud dev environments.
#
# Optional environment variables:
#   VIBECODE_INSTALL_AGENT=auto|yes|no
#   VIBECODE_AGENT_MODE=auto|systemd|local|none
#   VIBECODE_LISTEN=127.0.0.1:8787
#   VIBECODE_PROJECT_PATH=/path/to/project
#   VIBECODE_GO_VERSION=1.24.13
#   VIBECODE_NODE_MAJOR=22

log()  { printf '\033[1;34m[VibeCode]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[VibeCode]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[VibeCode]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[VibeCode]\033[0m %s\n' "$*" >&2; exit 1; }

INSTALL_AGENT="${VIBECODE_INSTALL_AGENT:-auto}"
AGENT_MODE="${VIBECODE_AGENT_MODE:-auto}"
LISTEN="${VIBECODE_LISTEN:-127.0.0.1:8787}"
GO_VERSION="${VIBECODE_GO_VERSION:-1.24.13}"
NODE_MAJOR="${VIBECODE_NODE_MAJOR:-22}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_PATH="${VIBECODE_PROJECT_PATH:-${REPO_ROOT}}"
LOCAL_BIN="${HOME}/.local/bin"
LOCAL_CONFIG="${HOME}/.config/vibecode"
LOCAL_DATA="${HOME}/.vibecode"

mkdir -p "${LOCAL_BIN}" "${LOCAL_CONFIG}" "${LOCAL_DATA}"
export PATH="${LOCAL_BIN}:${HOME}/.local/go/bin:${PATH}"

ensure_path_persisted() {
  local line='export PATH="$HOME/.local/bin:$HOME/.local/go/bin:$PATH"'
  local file
  for file in "${HOME}/.profile" "${HOME}/.bashrc"; do
    touch "${file}"
    if ! grep -Fq '$HOME/.local/bin:$HOME/.local/go/bin' "${file}"; then
      printf '\n# VibeCode worker tools\n%s\n' "${line}" >> "${file}"
    fi
  done
}

have_root_install() {
  [[ "${EUID}" -eq 0 ]] || command -v sudo >/dev/null 2>&1
}

as_root() {
  if [[ "${EUID}" -eq 0 ]]; then "$@"; else sudo "$@"; fi
}

install_base_packages() {
  local missing=() cmd
  for cmd in curl git jq openssl tmux rg tar xz sha256sum; do
    command -v "${cmd}" >/dev/null 2>&1 || missing+=("${cmd}")
  done
  ((${#missing[@]} == 0)) && { ok "Base packages already available"; return; }

  if command -v apt-get >/dev/null 2>&1 && have_root_install; then
    log "Installing base packages with apt..."
    as_root apt-get update
    as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y \
      ca-certificates curl git jq openssl tmux ripgrep tar xz-utils coreutils
  else
    warn "Cannot auto-install base packages (no apt+root/sudo). Missing: ${missing[*]}"
  fi
}

version_ge() {
  [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n1)" == "$2" ]]
}

node_is_ok() {
  command -v node >/dev/null 2>&1 || return 1
  command -v npm >/dev/null 2>&1 || return 1
  local major
  major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  [[ "${major}" =~ ^[0-9]+$ ]] && (( major >= 18 ))
}

install_node_user() {
  node_is_ok && { ok "Using $(node --version) / npm $(npm --version)"; return; }
  command -v curl >/dev/null 2>&1 || { warn "curl missing; cannot install Node.js"; return; }
  command -v tar >/dev/null 2>&1 || { warn "tar missing; cannot install Node.js"; return; }
  command -v sha256sum >/dev/null 2>&1 || { warn "sha256sum missing; cannot verify Node.js"; return; }

  local node_arch sums file url tmp expected
  case "$(uname -m)" in
    x86_64|amd64) node_arch="x64" ;;
    aarch64|arm64) node_arch="arm64" ;;
    *) warn "Unsupported Node architecture: $(uname -m)"; return ;;
  esac

  tmp="$(mktemp -d)"
  sums="${tmp}/SHASUMS256.txt"
  url="https://nodejs.org/dist/latest-v${NODE_MAJOR}.x"
  log "Installing user-local Node.js ${NODE_MAJOR}.x from nodejs.org..."
  curl -fsSL --retry 3 "${url}/SHASUMS256.txt" -o "${sums}"
  file="$(awk -v a="linux-${node_arch}.tar.xz" '$2 ~ a"$" {print $2; exit}' "${sums}")"
  [[ -n "${file}" ]] || { rm -rf "${tmp}"; warn "Could not resolve Node.js archive"; return; }
  curl -fsSL --retry 3 "${url}/${file}" -o "${tmp}/${file}"
  expected="$(awk -v f="${file}" '$2==f {print $1}' "${sums}")"
  (cd "${tmp}" && printf '%s  %s\n' "${expected}" "${file}" | sha256sum -c - >/dev/null)

  rm -rf "${HOME}/.local/node-${NODE_MAJOR}"
  mkdir -p "${HOME}/.local/node-${NODE_MAJOR}"
  tar -xJf "${tmp}/${file}" --strip-components=1 -C "${HOME}/.local/node-${NODE_MAJOR}"
  rm -rf "${tmp}"
  ln -sfn "${HOME}/.local/node-${NODE_MAJOR}/bin/node" "${LOCAL_BIN}/node"
  ln -sfn "${HOME}/.local/node-${NODE_MAJOR}/bin/npm" "${LOCAL_BIN}/npm"
  ln -sfn "${HOME}/.local/node-${NODE_MAJOR}/bin/npx" "${LOCAL_BIN}/npx"
  [[ -x "${HOME}/.local/node-${NODE_MAJOR}/bin/corepack" ]] && \
    ln -sfn "${HOME}/.local/node-${NODE_MAJOR}/bin/corepack" "${LOCAL_BIN}/corepack"
  export PATH="${HOME}/.local/node-${NODE_MAJOR}/bin:${LOCAL_BIN}:${PATH}"
  ok "Installed $(node --version)"
}

configure_npm_prefix() {
  command -v npm >/dev/null 2>&1 || return 0
  npm config set prefix "${HOME}/.local" >/dev/null
}

install_claude() {
  if command -v claude >/dev/null 2>&1; then
    ok "Claude Code already installed: $(claude --version 2>/dev/null | head -n1 || command -v claude)"
    return
  fi
  command -v npm >/dev/null 2>&1 || { warn "npm unavailable; Claude Code not installed"; return; }
  log "Installing Claude Code..."
  npm install -g @anthropic-ai/claude-code
  command -v claude >/dev/null 2>&1 && ok "Claude Code installed" || warn "Claude Code installed but is not on PATH"
}

install_codex() {
  if command -v codex >/dev/null 2>&1; then
    ok "Codex already installed: $(codex --version 2>/dev/null | head -n1 || command -v codex)"
    return
  fi
  command -v npm >/dev/null 2>&1 || { warn "npm unavailable; Codex not installed"; return; }
  log "Installing OpenAI Codex CLI..."
  npm install -g @openai/codex
  command -v codex >/dev/null 2>&1 && ok "Codex installed" || warn "Codex installed but is not on PATH"
}

install_grok() {
  if command -v grok >/dev/null 2>&1; then
    ok "Grok Build already installed: $(grok --version 2>/dev/null | head -n1 || command -v grok)"
    return
  fi
  command -v curl >/dev/null 2>&1 || { warn "curl unavailable; Grok Build not installed"; return; }
  log "Installing Grok Build from xAI..."
  curl -fsSL https://x.ai/cli/install.sh | bash
  export PATH="${HOME}/.local/bin:${PATH}"
  command -v grok >/dev/null 2>&1 && ok "Grok Build installed" || warn "Grok installer completed; open a new shell if 'grok' is not yet on PATH"
}

go_is_ok() {
  command -v go >/dev/null 2>&1 || return 1
  local v
  v="$(go env GOVERSION 2>/dev/null | sed 's/^go//' || true)"
  [[ -n "${v}" ]] && version_ge "${v}" "1.23"
}

install_go_user() {
  go_is_ok && { ok "Using $(go version)"; return; }
  command -v curl >/dev/null 2>&1 || { warn "curl missing; cannot install Go"; return; }
  command -v tar >/dev/null 2>&1 || { warn "tar missing; cannot install Go"; return; }

  local arch archive tmp
  case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) warn "Unsupported Go architecture: $(uname -m)"; return ;;
  esac
  archive="go${GO_VERSION}.linux-${arch}.tar.gz"
  tmp="$(mktemp -d)"
  log "Installing user-local Go ${GO_VERSION}..."
  curl -fsSL --retry 3 "https://go.dev/dl/${archive}" -o "${tmp}/${archive}"
  rm -rf "${HOME}/.local/go"
  mkdir -p "${HOME}/.local"
  tar -C "${HOME}/.local" -xzf "${tmp}/${archive}"
  rm -rf "${tmp}"
  export PATH="${HOME}/.local/go/bin:${PATH}"
  go_is_ok && ok "Installed $(go version)" || warn "Go installation failed"
}

can_use_systemd() {
  command -v systemctl >/dev/null 2>&1 && [[ -d /run/systemd/system ]] && have_root_install
}

resolve_agent_mode() {
  case "${AGENT_MODE}" in
    none|systemd|local) printf '%s' "${AGENT_MODE}" ;;
    auto) if can_use_systemd; then printf 'systemd'; else printf 'local'; fi ;;
    *) die "Invalid VIBECODE_AGENT_MODE=${AGENT_MODE}" ;;
  esac
}

should_install_agent() {
  case "${INSTALL_AGENT}" in
    yes) return 0 ;;
    no) return 1 ;;
    auto) [[ -f "${REPO_ROOT}/agent/go.mod" ]] ;;
    *) die "Invalid VIBECODE_INSTALL_AGENT=${INSTALL_AGENT}" ;;
  esac
}

install_agent_systemd() {
  log "Installing VibeCode Agent in systemd mode..."
  VIBECODE_USER="$(id -un)" \
  VIBECODE_LISTEN="${LISTEN}" \
  VIBECODE_PROJECTS_ROOT="$(dirname "${PROJECT_PATH}")" \
    "${REPO_ROOT}/deploy/install-agent.sh"
}

create_local_agent_config() {
  local cfg="${LOCAL_CONFIG}/config.json"
  [[ -f "${cfg}" ]] && { ok "Keeping existing ${cfg}"; return; }
  command -v jq >/dev/null 2>&1 || { warn "jq unavailable; cannot generate local Agent config"; return; }
  command -v openssl >/dev/null 2>&1 || { warn "openssl unavailable; cannot generate Agent token"; return; }

  local token claude_cmd codex_cmd grok_cmd
  token="$(openssl rand -hex 32)"
  claude_cmd="$(command -v claude || true)"
  codex_cmd="$(command -v codex || true)"
  grok_cmd="$(command -v grok || true)"

  jq -n \
    --arg listen "${LISTEN}" \
    --arg token "${token}" \
    --arg dataDir "${LOCAL_DATA}" \
    --arg projectPath "${PROJECT_PATH}" \
    --arg claude "${claude_cmd:-claude}" \
    --arg codex "${codex_cmd:-codex}" \
    --arg grok "${grok_cmd:-grok}" \
    '{listen:$listen,token:$token,dataDir:$dataDir,maxUploadMB:50,
      projects:[{id:"workspace",name:"Workspace",path:$projectPath}],
      providers:{claude:{command:$claude,args:[]},codex:{command:$codex,args:[]},grok:{command:$grok,args:[]}}}' \
    > "${cfg}"
  chmod 600 "${cfg}"
  ok "Created ${cfg} (token hidden)"
}

install_agent_local() {
  install_go_user
  go_is_ok || { warn "Go >=1.23 unavailable; skipping local Agent build"; return; }
  [[ -f "${REPO_ROOT}/agent/go.mod" ]] || { warn "agent/go.mod not found; skipping Agent"; return; }

  log "Building user-local VibeCode Agent..."
  (
    cd "${REPO_ROOT}/agent"
    go mod download
    go test ./...
    go build -trimpath -ldflags='-s -w' -o "${LOCAL_BIN}/vibecode-agent" ./cmd/vibecode-agent
  )
  create_local_agent_config
  ok "Installed ${LOCAL_BIN}/vibecode-agent"
}

install_agent() {
  should_install_agent || { warn "VibeCode Agent install disabled"; return; }
  case "$(resolve_agent_mode)" in
    systemd) install_agent_systemd ;;
    local) install_agent_local ;;
    none) warn "VibeCode Agent mode is none; skipping" ;;
  esac
}

print_next_steps() {
  cat <<'NEXT'

============================================================
VibeCode worker bootstrap complete
============================================================
Authentication is intentionally NOT automated.

Next authentication steps (run only when you are ready):
  Claude Code : claude
  Codex       : codex   (choose Sign in with ChatGPT when offered)
  Grok Build  : grok login --device-auth   # best for SSH/headless

Then verify this machine with:
  bash deploy/check-worker.sh

If this environment has no systemd, start the local Agent with:
  bash deploy/start-agent-local.sh

Security:
- Do not paste provider OAuth tokens into scripts or Git.
- Keep VibeCode Agent on loopback/private networking unless intentionally exposed.
- A cloud Work/devbox environment may be ephemeral; re-run this script after reset.
============================================================
NEXT
}

main() {
  log "Repository : ${REPO_ROOT}"
  log "User       : $(id -un)"
  log "Home       : ${HOME}"
  log "Project    : ${PROJECT_PATH}"
  log "Agent bind : ${LISTEN}"
  ensure_path_persisted
  install_base_packages
  install_node_user
  configure_npm_prefix
  install_claude
  install_codex
  install_grok
  install_agent
  print_next_steps
}

main "$@"
