package filesvc

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestSafeJoinRejectsEscape(t *testing.T) {
	if _, err := SafeJoin("/tmp/project", "../../etc/passwd"); err != nil {
		return
	}
	t.Fatal("expected escape to be rejected")
}

func TestWriteUpdatesExistingFile(t *testing.T) {
	root := t.TempDir()
	path := filepath.Join(root, "main.go")
	if err := os.WriteFile(path, []byte("before\n"), 0o640); err != nil {
		t.Fatal(err)
	}

	if err := Write(root, "main.go", []byte("after\n"), 1024); err != nil {
		t.Fatal(err)
	}
	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "after\n" {
		t.Fatalf("unexpected content: %q", got)
	}
	st, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if st.Mode().Perm() != 0o640 {
		t.Fatalf("permissions changed: %o", st.Mode().Perm())
	}
}

func TestWriteRejectsTooLargeContent(t *testing.T) {
	root := t.TempDir()
	path := filepath.Join(root, "a.txt")
	if err := os.WriteFile(path, []byte("old"), 0o600); err != nil {
		t.Fatal(err)
	}

	if err := Write(root, "a.txt", []byte(strings.Repeat("x", 11)), 10); err == nil {
		t.Fatal("expected edit size limit error")
	}
}

func TestWriteRejectsSymlinkEscape(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("symlink creation may require elevated privileges on Windows")
	}

	root := t.TempDir()
	outside := t.TempDir()
	outsideFile := filepath.Join(outside, "secret.txt")
	if err := os.WriteFile(outsideFile, []byte("do not change"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outsideFile, filepath.Join(root, "link.txt")); err != nil {
		t.Fatal(err)
	}

	if err := Write(root, "link.txt", []byte("changed"), 1024); err == nil {
		t.Fatal("expected symlink escape to be rejected")
	}
	got, err := os.ReadFile(outsideFile)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "do not change" {
		t.Fatalf("outside file was modified: %q", got)
	}
}
