#!/usr/bin/env bash
set -Eeuo pipefail

# VibeCode Agent installer for Ubuntu 22.04 / 24.04.
#
# Run from a cloned VibeCode repository:
#   chmod +x deploy/install-agent.sh
#   ./deploy/install-agent.sh
#
# Optional overrides:
#   VIBECODE_USER=ubuntu \
#   VIBECODE_LISTEN=127.0.0.1:8787 \
#   VIBECODE_PROJECTS_ROOT=/home/ubuntu/projects \
#   GO_VERSION=1.24.13 \
#   ./deploy/install-agent.sh

readonly APP_NAME="vibecode-agent"
readonly INSTALL_DIR="/opt/vibecode"
readonly CONFIG_DIR="/etc/vibecode"
readonly CONFIG_FILE="${CONFIG_DIR}/config.json"
readonly SERVICE_FILE="/etc/systemd/system/vibecode-agent.service"
readonly REQUIRED_GO_MAJOR=1
readonly REQUIRED_GO_MINOR=23
readonly DEFAULT_GO_VERSION="1.24.13"

log()  { printf '\033[1;34m[VibeCode]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[VibeCode]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[VibeCode]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[VibeCode]\033[0m %s\n' "$*" >&2; exit 1; }

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  command -v sudo >/dev/null 2>&1 || die "sudo is required when not running as root"
  SUDO="sudo"
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENT_DIR="${REPO_ROOT}/agent"
[[ -f "${AGENT_DIR}/go.mod" ]] || die "Run this script from the VibeCode repository; agent/go.mod was not found"

resolve_target_user() {
  if [[ -n "${VIBECODE_USER:-}" ]]; then
    printf '%s' "${VIBECODE_USER}"
    return
  fi

  if [[ -n "${SUDO_USER:-}" && "${SUDO_USER}" != "root" ]]; then
    printf '%s' "${SUDO_USER}"
    return
  fi

  if [[ "${EUID}" -ne 0 ]]; then
    id -un
    return
  fi

  local uid1000
  uid1000="$(getent passwd 1000 | cut -d: -f1 || true)"
  [[ -n "${uid1000}" ]] || die "Cannot determine the non-root service user. Re-run with VIBECODE_USER=youruser"
  printf '%s' "${uid1000}"
}

TARGET_USER="$(resolve_target_user)"
id "${TARGET_USER}" >/dev/null 2>&1 || die "User '${TARGET_USER}' does not exist"
TARGET_HOME="$(getent passwd "${TARGET_USER}" | cut -d: -f6)"
TARGET_GROUP="$(id -gn "${TARGET_USER}")"
[[ -n "${TARGET_HOME}" ]] || die "Could not resolve home directory for ${TARGET_USER}"

DATA_DIR="${VIBECODE_DATA_DIR:-${TARGET_HOME}/.vibecode}"
PROJECTS_ROOT="${VIBECODE_PROJECTS_ROOT:-${TARGET_HOME}/projects}"
LISTEN="${VIBECODE_LISTEN:-127.0.0.1:8787}"
GO_VERSION="${GO_VERSION:-${DEFAULT_GO_VERSION}}"

run_as_target() {
  if [[ "${EUID}" -eq 0 ]]; then
    runuser -u "${TARGET_USER}" -- "$@"
  else
    sudo -u "${TARGET_USER}" -H "$@"
  fi
}

install_packages() {
  log "Installing required Ubuntu packages..."
  ${SUDO} apt-get update
  DEBIAN_FRONTEND=noninteractive ${SUDO} apt-get install -y \
    ca-certificates curl git jq openssl ripgrep tmux tar gzip
}

go_is_new_enough() {
  command -v go >/dev/null 2>&1 || return 1

  local version major minor
  version="$(go env GOVERSION 2>/dev/null | sed 's/^go//' || true)"
  major="${version%%.*}"
  minor="${version#*.}"
  minor="${minor%%.*}"

  [[ "${major}" =~ ^[0-9]+$ && "${minor}" =~ ^[0-9]+$ ]] || return 1
  (( major > REQUIRED_GO_MAJOR || (major == REQUIRED_GO_MAJOR && minor >= REQUIRED_GO_MINOR) ))
}

install_go() {
  if go_is_new_enough; then
    ok "Using $(go version)"
    return
  fi

  local arch archive url tmp
  case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) die "Unsupported CPU architecture: $(uname -m)" ;;
  esac

  archive="go${GO_VERSION}.linux-${arch}.tar.gz"
  url="https://go.dev/dl/${archive}"
  tmp="$(mktemp -d)"

  log "Installing Go ${GO_VERSION} for ${arch}..."
  curl -fL --retry 3 --connect-timeout 15 "${url}" -o "${tmp}/${archive}"
  ${SUDO} rm -rf /usr/local/go
  ${SUDO} tar -C /usr/local -xzf "${tmp}/${archive}"
  rm -rf "${tmp}"

  export PATH="/usr/local/go/bin:${PATH}"
  go_is_new_enough || die "Go installation completed but Go >= 1.23 is still not available"
  ok "Installed $(go version)"
}

find_user_command() {
  local command_name="$1"
  run_as_target bash -lc "command -v '${command_name}' 2>/dev/null || true" | tail -n 1
}

install_binary() {
  log "Building VibeCode Agent..."
  pushd "${AGENT_DIR}" >/dev/null
  if [[ -x /usr/local/go/bin/go ]]; then
    /usr/local/go/bin/go mod download
    /usr/local/go/bin/go test ./...
    /usr/local/go/bin/go build -trimpath -ldflags="-s -w" -o "${APP_NAME}" ./cmd/vibecode-agent
  else
    go mod download
    go test ./...
    go build -trimpath -ldflags="-s -w" -o "${APP_NAME}" ./cmd/vibecode-agent
  fi
  popd >/dev/null

  ${SUDO} mkdir -p "${INSTALL_DIR}"
  ${SUDO} install -m 0755 "${AGENT_DIR}/${APP_NAME}" "${INSTALL_DIR}/${APP_NAME}"
  ok "Installed ${INSTALL_DIR}/${APP_NAME}"
}

ensure_directories() {
  ${SUDO} mkdir -p "${CONFIG_DIR}" "${DATA_DIR}" "${PROJECTS_ROOT}"
  ${SUDO} chown -R "${TARGET_USER}:${TARGET_GROUP}" "${DATA_DIR}" "${PROJECTS_ROOT}"
  ${SUDO} chmod 700 "${DATA_DIR}"
}

create_config() {
  if [[ -f "${CONFIG_FILE}" ]]; then
    warn "Keeping existing ${CONFIG_FILE}"
    return
  fi

  local token claude_cmd codex_cmd repo_path
  token="$(openssl rand -hex 32)"
  claude_cmd="$(find_user_command claude)"
  codex_cmd="$(find_user_command codex)"

  if [[ -z "${claude_cmd}" ]]; then
    claude_cmd="claude"
    warn "Claude Code CLI was not found for ${TARGET_USER}. Install/login to it before starting Claude sessions."
  fi
  if [[ -z "${codex_cmd}" ]]; then
    codex_cmd="codex"
    warn "Codex CLI was not found for ${TARGET_USER}. Install/login to it before starting Codex sessions."
  fi

  repo_path="$(realpath "${REPO_ROOT}")"
  if ! run_as_target test -r "${repo_path}"; then
    warn "${TARGET_USER} cannot read ${repo_path}. Move/chown the repo or edit ${CONFIG_FILE} after installation."
  fi

  jq -n \
    --arg listen "${LISTEN}" \
    --arg token "${token}" \
    --arg dataDir "${DATA_DIR}" \
    --arg projectPath "${repo_path}" \
    --arg claude "${claude_cmd}" \
    --arg codex "${codex_cmd}" \
    '{
      listen: $listen,
      token: $token,
      dataDir: $dataDir,
      maxUploadMB: 50,
      projects: [
        {id: "vibecode", name: "VibeCode", path: $projectPath}
      ],
      providers: {
        claude: {command: $claude, args: []},
        codex: {command: $codex, args: []}
      }
    }' | ${SUDO} tee "${CONFIG_FILE}" >/dev/null

  ${SUDO} chmod 600 "${CONFIG_FILE}"
  ok "Created ${CONFIG_FILE}"
}

create_service() {
  local service_path
  service_path="${TARGET_HOME}/.local/bin:${TARGET_HOME}/.npm-global/bin:/usr/local/bin:/usr/local/go/bin:/usr/bin:/bin"

  log "Creating systemd service for user ${TARGET_USER}..."
  ${SUDO} tee "${SERVICE_FILE}" >/dev/null <<EOF
[Unit]
Description=VibeCode Agent
Documentation=https://github.com/gutbai/vibecode
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${TARGET_USER}
Group=${TARGET_GROUP}
WorkingDirectory=${INSTALL_DIR}
Environment=HOME=${TARGET_HOME}
Environment=PATH=${service_path}
ExecStart=${INSTALL_DIR}/${APP_NAME} -config ${CONFIG_FILE}
Restart=always
RestartSec=3
TimeoutStopSec=10
# Keep tmux-hosted coding sessions alive when the agent itself restarts.
KillMode=process
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
EOF

  ${SUDO} chmod 644 "${SERVICE_FILE}"
}

start_service() {
  log "Starting VibeCode Agent..."
  ${SUDO} systemctl daemon-reload
  ${SUDO} systemctl enable "${APP_NAME}" >/dev/null
  ${SUDO} systemctl restart "${APP_NAME}"
  sleep 1

  if ${SUDO} systemctl is-active --quiet "${APP_NAME}"; then
    ok "VibeCode Agent is running"
  else
    ${SUDO} systemctl --no-pager --full status "${APP_NAME}" || true
    die "Agent failed to start. Check: journalctl -u ${APP_NAME} -n 100 --no-pager"
  fi
}

health_check() {
  local hostport health_url
  hostport="${LISTEN}"

  if [[ "${hostport}" == 0.0.0.0:* ]]; then
    hostport="127.0.0.1:${hostport##*:}"
  fi

  health_url="http://${hostport}/api/health"
  if curl -fsS --max-time 3 "${health_url}" >/dev/null 2>&1; then
    ok "Health check OK: ${health_url}"
  else
    warn "Service is active, but ${health_url} did not return success. Check: sudo journalctl -u ${APP_NAME} -n 100 --no-pager"
  fi
}

print_summary() {
  local token
  token="$(${SUDO} jq -r '.token' "${CONFIG_FILE}" 2>/dev/null || true)"

  cat <<EOF

============================================================
VibeCode Agent installation complete
============================================================
Service user : ${TARGET_USER}
Listen       : ${LISTEN}
Config       : ${CONFIG_FILE}
Data         : ${DATA_DIR}
Projects root: ${PROJECTS_ROOT}
Binary       : ${INSTALL_DIR}/${APP_NAME}

Android connection values:
  URL   : http://YOUR_VPS_OR_TUNNEL:8787
  Token : ${token}

Useful commands:
  sudo systemctl status vibecode-agent
  sudo systemctl restart vibecode-agent
  sudo journalctl -u vibecode-agent -f
  sudo -u ${TARGET_USER} tmux ls

Edit projects/providers:
  sudo nano ${CONFIG_FILE}
  sudo systemctl restart vibecode-agent

SECURITY:
The default listener is 127.0.0.1:8787, so it is NOT exposed publicly.
Recommended remote access: Tailscale or Cloudflare Tunnel with HTTPS.
If you deliberately set VIBECODE_LISTEN=0.0.0.0:8787, protect the port
with a firewall and keep the generated token private.
============================================================
EOF
}

main() {
  log "Repository: ${REPO_ROOT}"
  log "Service user: ${TARGET_USER} (${TARGET_HOME})"
  install_packages
  install_go
  ensure_directories
  install_binary
  create_config
  create_service
  start_service
  health_check
  print_summary
}

main "$@"
