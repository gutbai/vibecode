package session

import (
	"context"
	"os/exec"
)

func run(ctx context.Context, name string, args ...string) error {
	cmd := exec.CommandContext(ctx, name, args...)
	return cmd.Run()
}

func output(ctx context.Context, name string, args ...string) (string, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	b, err := cmd.CombinedOutput()
	return string(b), err
}
