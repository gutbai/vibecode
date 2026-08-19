package logbuf

import (
    "strings"
    "sync"
    "time"
)

type Entry struct {
    At      time.Time `json:"at"`
    Level   string    `json:"level"`
    Source  string    `json:"source"`
    Message string    `json:"message"`
}

const maxEntries = 1000

var state struct {
    sync.Mutex
    entries []Entry
}

func Add(level, source, message string) {
    state.Lock()
    defer state.Unlock()
    entry := Entry{
        At: time.Now().UTC(),
        Level: strings.ToUpper(strings.TrimSpace(level)),
        Source: strings.TrimSpace(source),
        Message: sanitize(message),
    }
    state.entries = append(state.entries, entry)
    if len(state.entries) > maxEntries {
        state.entries = append([]Entry(nil), state.entries[len(state.entries)-maxEntries:]...)
    }
}

func List(limit int) []Entry {
    state.Lock()
    defer state.Unlock()
    if limit <= 0 || limit > maxEntries {
        limit = 300
    }
    start := len(state.entries) - limit
    if start < 0 {
        start = 0
    }
    out := make([]Entry, len(state.entries)-start)
    copy(out, state.entries[start:])
    return out
}

func sanitize(s string) string {
    s = strings.ReplaceAll(s, "\r", " ")
    s = strings.ReplaceAll(s, "\n", " ")
    if len(s) > 4000 {
        s = s[:4000]
    }
    return s
}
