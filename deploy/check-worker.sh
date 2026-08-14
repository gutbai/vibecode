#!/usr/bin/env bash
set -Eeuo pipefail

export PATH="$HOME/.local/bin:$HOME/.local/go/bin:$PATH"

row() {
  local name="$1" cmd="$2" version_cmd="$3"
  if command -v "$cmd" >/dev/null 2>&1; then
    local path version
    path="$(command -v "$cmd")"
    version="$(bash -lc "$version_cmd" 2>/dev/null | head -n1 || true)"
    printf '%-16s %-10s %-42s %s\n' "$name" "yes" "$path" "${version:-installed}"
  else
    printf '%-16s %-10s %-42s %s\n' "$name" "no" "-" "-"
  fi
}

printf '%-16s %-10s %-42s %s\n' "Component" "Installed" "Path" "Version/status"
printf '%-16s %-10s %-42s %s\n' "----------------" "----------" "------------------------------------------" "----------------"
row "Claude Code" claude 'claude --version'
row "Codex CLI" codex 'codex --version'
row "Grok Build" grok 'grok --version || grok version'
row "tmux" tmux 'tmux -V'
row "ripgrep" rg 'rg --version'
row "git" git 'git --version'
row "Go" go 'go version'
row "Node.js" node 'node --version'
row "VibeCode Agent" vibecode-agent 'vibecode-agent -h 2>&1 | head -n1'

printf '\nEnvironment\n'
printf '  user      : %s\n' "$(id -un)"
printf '  home      : %s\n' "$HOME"
printf '  os        : %s\n' "$(uname -srmo)"
printf '  systemd   : %s\n' "$(if command -v systemctl >/dev/null 2>&1 && [[ -d /run/systemd/system ]]; then echo yes; else echo no; fi)"
printf '  tailscale : %s\n' "$(if command -v tailscale >/dev/null 2>&1; then tailscale status --json >/dev/null 2>&1 && echo connected || echo installed-not-connected; else echo not-installed; fi)"

if [[ -f /etc/vibecode/config.json ]]; then
  printf '  agent cfg : /etc/vibecode/config.json\n'
elif [[ -f "$HOME/.config/vibecode/config.json" ]]; then
  printf '  agent cfg : %s/.config/vibecode/config.json\n' "$HOME"
else
  printf '  agent cfg : missing\n'
fi

printf '\nAuthentication is not probed automatically to avoid triggering login flows.\n'
printf 'Use: claude | codex | grok login --device-auth\n'
