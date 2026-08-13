#!/usr/bin/env bash
set -euo pipefail
if ! command -v tmux >/dev/null; then echo "tmux is required"; exit 1; fi
if ! command -v rg >/dev/null; then echo "ripgrep (rg) is required"; exit 1; fi
cd "$(dirname "$0")/../agent"
go build -trimpath -ldflags="-s -w" -o vibecode-agent ./cmd/vibecode-agent
sudo install -m 0755 vibecode-agent /opt/vibecode/vibecode-agent
sudo mkdir -p /etc/vibecode
if [ ! -f /etc/vibecode/config.json ]; then sudo cp config.example.json /etc/vibecode/config.json; fi
sudo cp ../deploy/vibecode-agent.service /etc/systemd/system/vibecode-agent.service
cat <<MSG
Installed. Next:
1. Edit /etc/vibecode/config.json
2. Adjust User/paths in /etc/systemd/system/vibecode-agent.service
3. sudo systemctl daemon-reload
4. sudo systemctl enable --now vibecode-agent
Expose the local listener through Tailscale, Cloudflare Tunnel, or your HTTPS reverse proxy.
MSG
