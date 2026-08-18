package slashsvc

import (
	"os"
	"path/filepath"
	"testing"
)

func TestListDiscoversClaudeProjectSkill(t *testing.T) {
	project := t.TempDir()
	dir := filepath.Join(project, ".claude", "skills", "fix-ui")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	content := "---\nname: fix-ui\ndescription: Fix the mobile UI\n---\n# Skill\n"
	if err := os.WriteFile(filepath.Join(dir, "SKILL.md"), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}

	items := List("claude", project)
	for _, item := range items {
		if item.Command == "/fix-ui" {
			if item.Kind != "SKILL" || item.Description != "Fix the mobile UI" {
				t.Fatalf("unexpected skill: %#v", item)
			}
			return
		}
	}
	t.Fatalf("project skill not discovered: %#v", items)
}

func TestCodexSkillsUseDollarInvocation(t *testing.T) {
	project := t.TempDir()
	dir := filepath.Join(project, ".codex", "skills", "review-ci")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "SKILL.md"), []byte("---\nname: review-ci\n---\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	items := List("codex", project)
	for _, item := range items {
		if item.Command == "$review-ci" && item.Kind == "SKILL" {
			return
		}
	}
	t.Fatalf("Codex skill not discovered: %#v", items)
}
