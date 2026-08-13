package filesvc

import "testing"

func TestSafeJoinRejectsEscape(t *testing.T) {
	if _, err := SafeJoin("/tmp/project", "../../etc/passwd"); err != nil {
		return
	}
	t.Fatal("expected escape to be rejected")
}
