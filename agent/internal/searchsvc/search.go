package searchsvc

import (
	"context"
	"fmt"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gutbai/vibecode/agent/internal/model"
)

func Search(ctx context.Context, root, query string, limit int) ([]model.SearchResult, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return []model.SearchResult{}, nil
	}
	if limit <= 0 || limit > 500 {
		limit = 200
	}

	// Project search is intentionally literal, not regex. This makes searches such
	// as "2.0" match the actual dot instead of regex '.' matching any character.
	// We search case-insensitively here; the Android client can apply an exact-case
	// filter for its Aa toggle without having to issue a second protocol shape.
	args := []string{
		"-n",
		"--no-heading",
		"--color", "never",
		"--fixed-strings",
		"--ignore-case",
		"--max-count", strconv.Itoa(limit),
		"--",
		query,
		".",
	}
	cmd := exec.CommandContext(ctx, "rg", args...)
	cmd.Dir = root
	b, err := cmd.CombinedOutput()
	if err != nil {
		if ee, ok := err.(*exec.ExitError); ok && ee.ExitCode() == 1 {
			return []model.SearchResult{}, nil
		}
		return nil, fmt.Errorf("ripgrep: %w: %s", err, string(b))
	}

	lines := strings.Split(strings.TrimSpace(string(b)), "\n")
	out := make([]model.SearchResult, 0, min(len(lines), limit))
	for _, line := range lines {
		if line == "" {
			continue
		}
		parts := strings.SplitN(line, ":", 3)
		if len(parts) < 3 {
			continue
		}
		n, _ := strconv.Atoi(parts[1])
		out = append(out, model.SearchResult{
			FilePath:   filepath.ToSlash(strings.TrimPrefix(parts[0], "./")),
			LineNumber: n,
			Preview:    parts[2],
		})
		if len(out) >= limit {
			break
		}
	}
	return out, nil
}
