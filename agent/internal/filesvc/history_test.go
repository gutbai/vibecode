package filesvc

import (
	"fmt"
	"testing"
)

func TestRevisionHistoryRoundTripAndPrune(t *testing.T) {
	dataDir := t.TempDir()
	for i := 0; i < maxHistoryEntries+3; i++ {
		content := []byte(fmt.Sprintf("version-%02d", i))
		if _, err := RecordRevision(dataDir, "project-1", "src/main.go", content); err != nil {
			t.Fatal(err)
		}
	}

	revisions, err := ListRevisions(dataDir, "project-1", "src/main.go")
	if err != nil {
		t.Fatal(err)
	}
	if len(revisions) != maxHistoryEntries {
		t.Fatalf("expected %d revisions, got %d", maxHistoryEntries, len(revisions))
	}

	content, revision, err := ReadRevision(dataDir, "project-1", "src/main.go", revisions[0].ID, 1024)
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "version-22" {
		t.Fatalf("unexpected latest revision content: %q", content)
	}
	if revision.SHA256 != SHA256(content) {
		t.Fatal("revision checksum mismatch")
	}
}

func TestRevisionHistoryDeduplicatesSameContent(t *testing.T) {
	dataDir := t.TempDir()
	content := []byte("same")
	if _, err := RecordRevision(dataDir, "project-1", "a.txt", content); err != nil {
		t.Fatal(err)
	}
	if _, err := RecordRevision(dataDir, "project-1", "a.txt", content); err != nil {
		t.Fatal(err)
	}
	revisions, err := ListRevisions(dataDir, "project-1", "a.txt")
	if err != nil {
		t.Fatal(err)
	}
	if len(revisions) != 1 {
		t.Fatalf("expected one deduplicated revision, got %d", len(revisions))
	}
}
