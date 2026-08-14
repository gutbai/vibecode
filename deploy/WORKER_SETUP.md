# VibeCode worker bootstrap

This setup is intended for Ubuntu/Debian VPSes and cloud development environments, including environments that do not expose `systemd`.

## What the bootstrap installs

`deploy/bootstrap-worker.sh` is idempotent and installs/checks:

- `git`, `curl`, `jq`, `openssl`, `tmux`, `ripgrep`
- Node.js 18+ (user-local Node 22 if the existing Node is too old/missing)
- Claude Code (`@anthropic-ai/claude-code`)
- OpenAI Codex CLI (`@openai/codex`)
- Grok Build (official xAI installer)
- Go 1.23+ when a local VibeCode Agent build is needed
- VibeCode Agent

It intentionally **does not authenticate any provider account**.

## Fast setup

```bash
git clone https://github.com/gutbai/vibecode.git
cd vibecode
chmod +x deploy/*.sh
./deploy/bootstrap-worker.sh
./deploy/check-worker.sh
```

The bootstrap is safe to run again after an ephemeral environment resets.

## Agent modes

The default is `VIBECODE_AGENT_MODE=auto`:

- A normal VPS with `systemd` + root/sudo uses `deploy/install-agent.sh`.
- A cloud/dev environment without `systemd` builds the Agent into `~/.local/bin/vibecode-agent` and creates `~/.config/vibecode/config.json`.

Force local mode:

```bash
VIBECODE_AGENT_MODE=local ./deploy/bootstrap-worker.sh
```

Disable Agent installation when you only want the coding CLIs:

```bash
VIBECODE_INSTALL_AGENT=no ./deploy/bootstrap-worker.sh
```

Use a specific project root:

```bash
VIBECODE_PROJECT_PATH="$HOME/my-project" ./deploy/bootstrap-worker.sh
```

Keep the default Agent listener on `127.0.0.1:8787` unless you deliberately configure a private transport such as Tailscale.

## Authentication

Authentication is a separate manual step so OAuth/session credentials are not copied into scripts, Git history, or logs.

```bash
# Claude Code: starts the official login flow
claude

# Codex: start it and choose Sign in with ChatGPT when offered
codex

# Grok Build on an SSH/headless host
grok login --device-auth
```

After authentication:

```bash
./deploy/check-worker.sh
```

## Local Agent (no systemd)

Foreground:

```bash
./deploy/start-agent-local.sh
```

Background for the lifetime of the current cloud machine/container:

```bash
./deploy/start-agent-local.sh background
```

Stop it:

```bash
./deploy/start-agent-local.sh stop
```

A cloud environment may terminate background processes when the environment is suspended or reset. Treat this mode as best-effort unless the platform explicitly guarantees persistence.

## Minimal prompt for ChatGPT Work / another cloud agent

```text
Create/use a Linux development environment for this task.

Clone or update:
https://github.com/gutbai/vibecode.git

Then run exactly this setup flow:

cd vibecode
chmod +x deploy/*.sh
VIBECODE_AGENT_MODE=local ./deploy/bootstrap-worker.sh
./deploy/check-worker.sh

Do not authenticate Claude, Codex, Grok, GitHub, or Tailscale on my behalf.
Do not print or invent any OAuth token/API key.
Do not expose port 8787 publicly.

When finished, give me only:
1. the output/summary from check-worker.sh,
2. whether this environment has systemd,
3. whether Tailscale is installed/usable,
4. which of Claude/Codex/Grok still require manual authentication,
5. whether the VibeCode Agent can remain running after the cloud task ends.
```
