package session

import (
	"context"
	"fmt"
	"strings"

	"github.com/gutbai/vibecode/agent/internal/config"
)

type Provider interface {
	Start(ctx context.Context, tmuxName, workdir string) error
	Send(ctx context.Context, tmuxName, text string) error
	Stop(ctx context.Context, tmuxName string) error
	Capture(ctx context.Context, tmuxName string, lines int) (string, error)
	Exists(ctx context.Context, tmuxName string) bool
}

type TMuxProvider struct{ spec config.Provider }

func NewTMuxProvider(spec config.Provider) *TMuxProvider { return &TMuxProvider{spec: spec} }

func (p *TMuxProvider) Start(ctx context.Context, tmuxName, workdir string) error {
	args := []string{"new-session", "-d", "-s", tmuxName, "-c", workdir, p.spec.Command}
	args = append(args, p.spec.Args...)
	return run(ctx, "tmux", args...)
}

func (p *TMuxProvider) Send(ctx context.Context, tmuxName, text string) error {
	if err := run(ctx, "tmux", "send-keys", "-t", tmuxName, "-l", text); err != nil {
		return err
	}
	return run(ctx, "tmux", "send-keys", "-t", tmuxName, "Enter")
}

func (p *TMuxProvider) Stop(ctx context.Context, tmuxName string) error {
	return run(ctx, "tmux", "kill-session", "-t", tmuxName)
}

func (p *TMuxProvider) Capture(ctx context.Context, tmuxName string, lines int) (string, error) {
	if lines <= 0 {
		lines = 200
	}
	out, err := output(ctx, "tmux", "capture-pane", "-p", "-t", tmuxName, "-S", fmt.Sprintf("-%d", lines))
	return strings.TrimSpace(out), err
}

func (p *TMuxProvider) Exists(ctx context.Context, tmuxName string) bool {
	return run(ctx, "tmux", "has-session", "-t", tmuxName) == nil
}
