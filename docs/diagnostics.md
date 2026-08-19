# VibeCode diagnostics

The Android app keeps a local diagnostic ring buffer and exposes it in the **Logs** tab.

- **APP**: Android/API/WebView/xterm/WebSocket lifecycle, with tokens redacted.
- **AGENT**: recent in-memory VibeCode Agent diagnostics from `/api/logs`.

The terminal screen also performs an HTTP probe of `/ws/terminal/{sessionId}` before connecting. A 404 usually means the VPS Agent is older than the real-PTY build or a reverse proxy does not route the terminal WebSocket path. A 400 from a plain HTTP probe is expected when the endpoint exists because a normal GET is not a WebSocket upgrade.
