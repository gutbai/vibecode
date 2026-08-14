#!/usr/bin/env bash
set -Eeuo pipefail

export PATH="$HOME/.local/bin:$HOME/.local/go/bin:$PATH"

AGENT="${VIBECODE_AGENT_BIN:-$(command -v vibecode-agent || true)}"
CONFIG="${VIBECODE_AGENT_CONFIG:-$HOME/.config/vibecode/config.json}"
MODE="${1:-foreground}"
LOG_DIR="$HOME/.vibecode/logs"
PID_FILE="$HOME/.vibecode/agent.pid"
LOG_FILE="$LOG_DIR/agent.log"

[[ -n "$AGENT" && -x "$AGENT" ]] || { echo "vibecode-agent not found. Run deploy/bootstrap-worker.sh first." >&2; exit 1; }
[[ -f "$CONFIG" ]] || { echo "Agent config not found: $CONFIG" >&2; exit 1; }
mkdir -p "$LOG_DIR"

case "$MODE" in
  foreground)
    exec "$AGENT" -config "$CONFIG"
    ;;
  background|--background|-d)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "VibeCode Agent already running with PID $(cat "$PID_FILE")"
      exit 0
    fi
    nohup "$AGENT" -config "$CONFIG" >>"$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 1
    if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "VibeCode Agent started in background: PID $(cat "$PID_FILE")"
      echo "Log: $LOG_FILE"
    else
      echo "Agent exited early. Check: $LOG_FILE" >&2
      exit 1
    fi
    ;;
  stop)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      kill "$(cat "$PID_FILE")"
      rm -f "$PID_FILE"
      echo "VibeCode Agent stopped"
    else
      echo "No local Agent process found"
      rm -f "$PID_FILE"
    fi
    ;;
  *)
    echo "Usage: $0 [foreground|background|stop]" >&2
    exit 2
    ;;
esac
