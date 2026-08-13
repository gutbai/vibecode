package session

import (
	"github.com/gutbai/vibecode/agent/internal/model"
	"testing"
)

func TestInferWaiting(t *testing.T) {
	if got := inferStatus("I need your input before continuing", true); got != model.StatusWaitingInput {
		t.Fatalf("got %s", got)
	}
}
func TestInferDone(t *testing.T) {
	if got := inferStatus("", false); got != model.StatusDone {
		t.Fatalf("got %s", got)
	}
}
