package logbuf

import "testing"

func TestListReturnsNewestEntries(t *testing.T) {
    state.Lock()
    state.entries = nil
    state.Unlock()

    Add("info", "test", "one")
    Add("warn", "test", "two")
    got := List(1)
    if len(got) != 1 || got[0].Message != "two" || got[0].Level != "WARN" {
        t.Fatalf("unexpected entries: %#v", got)
    }
}
