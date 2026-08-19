package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"

	"github.com/creack/pty"
	"github.com/gorilla/websocket"
	"github.com/gutbai/vibecode/agent/internal/logbuf"
)

type terminalClientMessage struct {
	Type string `json:"type"`
	Data string `json:"data,omitempty"`
	Cols int    `json:"cols,omitempty"`
	Rows int    `json:"rows,omitempty"`
}

func clampTerminalSize(v, fallback int) int {
	if v < 4 {
		return fallback
	}
	if v > 500 {
		return 500
	}
	return v
}

// terminalTextPayload keeps terminal output compatible with Android WebView.
// Some WebView versions establish the websocket correctly but fail to deliver
// binary websocket frames to xterm. Text frames are reliable there. The leading
// NUL guarantees the current JS path treats the frame as terminal text rather
// than accidentally parsing a JSON-looking terminal line; xterm ignores NUL.
func terminalTextPayload(data []byte) []byte {
	text := strings.ToValidUTF8(string(data), "\uFFFD")
	payload := make([]byte, 1, len(text)+1)
	return append(payload, text...)
}

// terminalWS bridges a browser/xterm client to a real tmux client running in a
// pseudo-terminal. The tmux session itself stays alive when the websocket is
// disconnected; only this attached client is terminated.
func (s *Server) terminalWS(w http.ResponseWriter, r *http.Request) {
	sessionID := r.PathValue("id")
	logbuf.Add("INFO", "terminal", fmt.Sprintf("connect request session=%s remote=%s", sessionID, r.RemoteAddr))

	token := strings.TrimSpace(r.URL.Query().Get("token"))
	if token != s.cfg.Token {
		logbuf.Add("WARN", "terminal", fmt.Sprintf("unauthorized session=%s", sessionID))
		errOut(w, http.StatusUnauthorized, fmt.Errorf("unauthorized"))
		return
	}

	sess, ok := s.sessions.Get(sessionID)
	if !ok {
		logbuf.Add("WARN", "terminal", fmt.Sprintf("session not found id=%s", sessionID))
		errOut(w, http.StatusNotFound, os.ErrNotExist)
		return
	}

	// Android uses a lightweight HTTP probe before opening the real websocket.
	// Treat a normal GET as a readiness check instead of feeding it to the
	// websocket upgrader, which would otherwise return a misleading HTTP 400.
	if !websocket.IsWebSocketUpgrade(r) {
		logbuf.Add("INFO", "terminal", fmt.Sprintf("probe OK session=%s tmux=%s", sessionID, sess.TMuxName))
		jsonOut(w, http.StatusOK, map[string]interface{}{
			"ok":      true,
			"session": sessionID,
			"tmux":    sess.TMuxName,
		})
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		logbuf.Add("ERROR", "terminal", fmt.Sprintf("websocket upgrade failed session=%s: %v", sessionID, err))
		return
	}
	defer conn.Close()
	logbuf.Add("INFO", "terminal", fmt.Sprintf("websocket OPEN session=%s tmux=%s transport=text", sessionID, sess.TMuxName))
	defer logbuf.Add("INFO", "terminal", fmt.Sprintf("websocket CLOSED session=%s", sessionID))

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	cols := clampTerminalSize(parsePositiveInt(r.URL.Query().Get("cols")), 80)
	rows := clampTerminalSize(parsePositiveInt(r.URL.Query().Get("rows")), 24)
	cmd := exec.CommandContext(ctx, "tmux", "attach-session", "-t", sess.TMuxName)
	cmd.Env = append(os.Environ(), "TERM=xterm-256color", "COLORTERM=truecolor")
	ptmx, err := pty.StartWithSize(cmd, &pty.Winsize{Cols: uint16(cols), Rows: uint16(rows)})
	if err != nil {
		logbuf.Add("ERROR", "terminal", fmt.Sprintf("PTY start failed session=%s tmux=%s: %v", sessionID, sess.TMuxName, err))
		_ = conn.WriteJSON(map[string]string{"type": "error", "message": err.Error()})
		return
	}
	defer ptmx.Close()
	logbuf.Add("INFO", "terminal", fmt.Sprintf("PTY attached session=%s size=%dx%d", sessionID, cols, rows))

	var writeMu sync.Mutex

	// tmux attach does not always repaint an already-running TUI immediately.
	// Send the currently visible pane first so xterm never opens as a blank
	// screen while waiting for the next Claude/Codex/Grok redraw.
	snapshotCmd := exec.CommandContext(ctx, "tmux", "capture-pane", "-p", "-e", "-t", sess.TMuxName)
	snapshotCmd.Env = os.Environ()
	if snapshot, snapErr := snapshotCmd.Output(); snapErr != nil {
		logbuf.Add("WARN", "terminal", fmt.Sprintf("initial pane capture failed session=%s: %v", sessionID, snapErr))
	} else if len(snapshot) > 0 {
		frame := make([]byte, 0, len(snapshot)+16)
		frame = append(frame, []byte("\x1b[2J\x1b[H")...)
		frame = append(frame, snapshot...)
		writeMu.Lock()
		writeErr := conn.WriteMessage(websocket.TextMessage, terminalTextPayload(frame))
		writeMu.Unlock()
		if writeErr != nil {
			logbuf.Add("WARN", "terminal", fmt.Sprintf("initial snapshot write failed session=%s: %v", sessionID, writeErr))
			return
		}
		logbuf.Add("INFO", "terminal", fmt.Sprintf("initial pane snapshot sent session=%s bytes=%d transport=text", sessionID, len(snapshot)))
	} else {
		logbuf.Add("INFO", "terminal", fmt.Sprintf("initial pane snapshot empty session=%s", sessionID))
	}

	processDone := make(chan struct{})
	go func() {
		err := cmd.Wait()
		if err != nil && ctx.Err() == nil {
			logbuf.Add("WARN", "terminal", fmt.Sprintf("tmux client exited session=%s: %v", sessionID, err))
		}
		close(processDone)
	}()

	keepaliveDone := make(chan struct{})
	defer close(keepaliveDone)
	go func() {
		ticker := time.NewTicker(20 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-keepaliveDone:
				return
			case <-ctx.Done():
				return
			case <-ticker.C:
				writeMu.Lock()
				err := conn.WriteControl(websocket.PingMessage, []byte("vibecode"), time.Now().Add(5*time.Second))
				writeMu.Unlock()
				if err != nil {
					logbuf.Add("WARN", "terminal", fmt.Sprintf("websocket ping failed session=%s: %v", sessionID, err))
					cancel()
					return
				}
			}
		}
	}()

	outputDone := make(chan struct{})
	var firstOutput sync.Once
	go func() {
		defer close(outputDone)
		buf := make([]byte, 32*1024)
		for {
			n, readErr := ptmx.Read(buf)
			if n > 0 {
				firstOutput.Do(func() {
					logbuf.Add("INFO", "terminal", fmt.Sprintf("PTY output started session=%s firstChunkBytes=%d transport=text", sessionID, n))
				})
				frame := append([]byte(nil), buf[:n]...)
				writeMu.Lock()
				writeErr := conn.WriteMessage(websocket.TextMessage, terminalTextPayload(frame))
				writeMu.Unlock()
				if writeErr != nil {
					logbuf.Add("WARN", "terminal", fmt.Sprintf("websocket write failed session=%s: %v", sessionID, writeErr))
					cancel()
					return
				}
			}
			if readErr != nil {
				if ctx.Err() == nil {
					logbuf.Add("WARN", "terminal", fmt.Sprintf("PTY read ended session=%s: %v", sessionID, readErr))
				}
				return
			}
		}
	}()

	for {
		select {
		case <-processDone:
			return
		case <-outputDone:
			return
		default:
		}

		messageType, payload, readErr := conn.ReadMessage()
		if readErr != nil {
			logbuf.Add("WARN", "terminal", fmt.Sprintf("websocket read ended session=%s: %v", sessionID, readErr))
			return
		}
		if messageType != websocket.TextMessage {
			continue
		}

		var message terminalClientMessage
		if err := json.Unmarshal(payload, &message); err != nil {
			logbuf.Add("WARN", "terminal", fmt.Sprintf("invalid client message session=%s: %v", sessionID, err))
			continue
		}
		switch strings.ToLower(strings.TrimSpace(message.Type)) {
		case "input":
			if message.Data != "" {
				if _, err := ptmx.Write([]byte(message.Data)); err != nil {
					logbuf.Add("ERROR", "terminal", fmt.Sprintf("PTY input failed session=%s: %v", sessionID, err))
					return
				}
			}
		case "resize":
			newCols := clampTerminalSize(message.Cols, cols)
			newRows := clampTerminalSize(message.Rows, rows)
			if err := pty.Setsize(ptmx, &pty.Winsize{Cols: uint16(newCols), Rows: uint16(newRows)}); err == nil {
				cols, rows = newCols, newRows
			} else {
				logbuf.Add("WARN", "terminal", fmt.Sprintf("resize failed session=%s: %v", sessionID, err))
			}
		case "ping":
			writeMu.Lock()
			_ = conn.WriteJSON(map[string]string{"type": "pong"})
			writeMu.Unlock()
		}
	}
}

func parsePositiveInt(raw string) int {
	var value int
	for _, r := range strings.TrimSpace(raw) {
		if r < '0' || r > '9' {
			return 0
		}
		value = value*10 + int(r-'0')
		if value > 10000 {
			return 0
		}
	}
	return value
}
