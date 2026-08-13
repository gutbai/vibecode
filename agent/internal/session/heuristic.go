package session

import (
	"github.com/gutbai/vibecode/agent/internal/model"
	"strings"
)

func inferStatus(out string, exists bool) model.SessionStatus {
	if !exists {
		return model.StatusDone
	}
	s := strings.ToLower(out)
	waitMarkers := []string{
		"need your input", "waiting for input", "please choose", "please confirm",
		"do you want", "would you like", "which option", "press enter to continue",
		"cần input", "chọn một", "xác nhận",
	}
	for _, m := range waitMarkers {
		if strings.Contains(s, m) {
			return model.StatusWaitingInput
		}
	}
	errMarkers := []string{"fatal error", "panic:", "permission denied", "authentication failed"}
	for _, m := range errMarkers {
		if strings.Contains(s, m) {
			return model.StatusError
		}
	}
	return model.StatusRunning
}
