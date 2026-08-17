package searchsvc

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestSearchTreatsQueryAsLiteralAndIgnoresCase(t *testing.T) {
	if _, err := exec.LookPath("rg"); err != nil {
		t.Skip("ripgrep is not installed")
	}

	root := t.TempDir()
	content := strings.Join([]string{
		"version 2.0",
		"version 200",
		"Name Abc",
		"name abc",
	}, "\n")
	if err := os.WriteFile(filepath.Join(root, "sample.txt"), []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}

	results, err := Search(context.Background(), root, "2.0", 100)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 1 {
		t.Fatalf("expected exactly one literal 2.0 result, got %d: %#v", len(results), results)
	}
	if !strings.Contains(results[0].Preview, "2.0") {
		t.Fatalf("expected preview to contain literal 2.0, got %q", results[0].Preview)
	}

	results, err = Search(context.Background(), root, "ABC", 100)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 2 {
		t.Fatalf("expected case-insensitive backend search to return both lines, got %d", len(results))
	}
}
