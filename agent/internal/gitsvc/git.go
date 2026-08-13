package gitsvc

import (
	"context"
	"fmt"
	"os/exec"
)

func run(ctx context.Context, root string, args ...string) (string, error) {
	full := append([]string{"-C", root}, args...)
	cmd := exec.CommandContext(ctx, "git", full...)
	b, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("git %v: %w: %s", args, err, string(b))
	}
	return string(b), nil
}

func Status(ctx context.Context, root string) (string, error) {
	return run(ctx, root, "status", "--short", "--branch")
}
func Diff(ctx context.Context, root string) (string, error) {
	return run(ctx, root, "diff", "--no-ext-diff", "--unified=3")
}
func Log(ctx context.Context, root string) (string, error) {
	return run(ctx, root, "log", "-n", "30", "--pretty=format:%h%x09%ad%x09%s", "--date=iso")
}
