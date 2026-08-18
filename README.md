# VibeCode

VibeCode is a lightweight Android remote-control client plus a Go agent for managing Claude Code, Codex and Grok sessions running on VPS/PC machines.

The source code and coding CLIs stay on the VPS. The Android app only talks to VibeCode Agent over HTTPS/WebSocket.

## What is implemented

### Sessions
- Start Claude Code, Codex or Grok inside isolated `tmux` sessions.
- Sessions survive Android disconnects and can still be attached to manually over SSH.
- States: `STARTING`, `RUNNING`, `WAITING_INPUT`, `DONE`, `ERROR`, `STOPPED`, `DISCONNECTED`.
- Live output polling from `tmux capture-pane` and WebSocket status/output events.
- Send text input back to a running session.
- Persistent session/message metadata in `~/.vibecode/state.json`.
- Session titles are derived automatically from the first meaningful prompt instead of requiring a title when the session is created.
- Slash suggestions can be discovered from the selected provider/project, including installed skills where supported.

### Attachments
- Android system document picker for images and arbitrary files.
- Clipboard URI paste button for copied images/files when Android exposes a content URI.
- Files upload to the VPS and are stored under `~/.vibecode/sessions/<session>/attachments/`.
- The agent sends the local VPS paths to the selected coding CLI so it can inspect them directly.
- SHA-256, MIME type, file size and original filename are retained.

### Project files, editing and search
- Project roots are explicitly whitelisted in `config.json`.
- Browse directories from Android without cloning the project to the phone.
- Preview and edit existing UTF-8 files up to 2 MB directly on the VPS.
- Open files for editing from either the file browser or search results, then save changes back to the remote project.
- Each opened file carries a SHA-256 revision; stale saves are rejected if the file changed on the VPS after it was opened.
- Successful VibeCode saves/restores snapshot the previous contents outside the source tree, allowing per-file history and guarded restore/undo.
- Remote writes are restricted to existing regular files inside configured project roots; traversal and symlink escapes are rejected.
- Project search uses literal/fixed-string `ripgrep` matching so punctuation such as the dot in `2.0` is treated literally rather than as regex syntax.
- Android search highlights matches, supports optional `Aa` case-sensitive filtering, opens the selected occurrence, and provides current/total plus previous/next navigation inside the file.

### Git
The Agent exposes read-only Git endpoints for status, diff and recent history. GitHub is not in the Android/VPS runtime path; normal `git pull/push` remains on the VPS.

### Machines
- Android stores multiple VibeCode Agent endpoints locally.
- Switch between VPS/PC machines.
- Basic hostname/OS/CPU/RAM information.
- Connection failures are surfaced in the UI instead of being silently swallowed; saved machine URL/token values are trimmed before use and machine entries can be edited.

## Architecture

```text
Android (Kotlin / Compose)
        |
        | HTTPS + WebSocket
        v
VibeCode Agent (Go)
        |
        +-- tmux -> Claude Code
        +-- tmux -> Codex
        +-- tmux -> Grok
        +-- filesystem
        +-- ripgrep
        +-- git
```

## VPS requirements

- Linux
- Go 1.24+ to build the Agent
- `tmux`
- `ripgrep` (`rg`)
- `git`
- Claude Code, Codex and/or Grok CLI installed/authenticated for the service user

## Agent quick start

```bash
cd agent
cp config.example.json config.json
# edit token, project paths and provider commands
go mod download
go run ./cmd/vibecode-agent -config config.json
```

The example listener is `127.0.0.1:8787`. Keep it private and expose it using your own authenticated HTTPS path (for example a VPN or reverse proxy). Do not expose a plain HTTP bearer-token endpoint directly to the public Internet.

### tmux compatibility

VibeCode owns sessions named `vibecode-<provider>-<id>`. You can inspect them manually:

```bash
tmux ls
tmux attach -t vibecode-claude-xxxxxxxx
```

## Android

Open `android/` with Android Studio or build from CLI:

```bash
cd android
gradle :app:assembleDebug
```

On first launch:

1. Open **Machines**.
2. Add the HTTPS/private-network URL of the Agent and its bearer token.
3. Select the machine.
4. Open **Sessions** or **Files**.

The Android app supports multiple server profiles. Credentials currently live in private app SharedPreferences; for a production release, migrate the token field to Android Keystore-backed encrypted storage.

## Message + attachment flow

```text
phone file/content URI
      |
      | multipart upload
      v
~/.vibecode/sessions/<id>/attachments/<attachment-id>-<name>
      |
      v
SessionMessage
      |
      v
tmux send-keys -> Claude/Codex/Grok
```

A message sent with files is converted to terminal input similar to:

```text
Please fix the UI based on these files.

Attached files on this machine:
- /home/ubuntu/.vibecode/sessions/sess_x/attachments/att_x-screen.png
- /home/ubuntu/.vibecode/sessions/sess_x/attachments/att_y-error.log
```

## API

See [`protocol/openapi.yaml`](protocol/openapi.yaml).

WebSocket endpoint:

```text
GET /ws?token=<agent-token>
```

Events currently include:

- `session.created`
- `session.output`
- `session.message`
- `session.status_changed`

## Status detection

The first implementation uses process existence plus terminal-output heuristics. Provider-specific adapters are intentionally isolated so Claude/Codex/Grok-specific structured hooks can replace heuristics later without changing the Android protocol.

## Deployment

A sample systemd unit and installer are in `deploy/`. Review the Linux username and allowed project paths before enabling it.

## Security notes

- Configure only explicit project roots.
- Use a long random bearer token.
- Keep Agent bound to loopback unless you have an authenticated/private network layer.
- Uploaded files are session-scoped and written with private directory permissions.
- Remote file editing only updates existing regular files under configured project roots and rejects traversal/symlink escapes.
- Stale editor saves/restores are rejected instead of silently overwriting a newer VPS-side version.
- VibeCode file history is stored outside the source tree under the configured Agent data directory.
- Android does not receive arbitrary shell execution endpoints; it can only invoke the supported session/file/search/slash/git API.

## Repository layout

```text
vibecode/
├── agent/        Go VPS agent
├── android/      Android Kotlin/Compose client
├── deploy/       systemd/install examples
├── protocol/     API contract
└── .github/      CI workflows
```
