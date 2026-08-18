package slashsvc

import (
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/gutbai/vibecode/agent/internal/model"
)

var builtins = map[string][]model.SlashItem{
	"claude": {
		{Command: "/help", Description: "Show available commands", Kind: "COMMAND"},
		{Command: "/model", Description: "Switch the model for this session", Kind: "COMMAND"},
		{Command: "/status", Description: "Show session and account status", Kind: "COMMAND"},
		{Command: "/context", Description: "Inspect context usage", Kind: "COMMAND"},
		{Command: "/compact", Description: "Compact conversation context", Kind: "COMMAND"},
		{Command: "/skills", Description: "List available skills", Kind: "COMMAND"},
		{Command: "/permissions", Description: "Review or change tool permissions", Kind: "COMMAND"},
		{Command: "/resume", Description: "Resume another conversation", Kind: "COMMAND"},
		{Command: "/rename", Description: "Rename or auto-name this session", Kind: "COMMAND"},
	},
	"codex": {
		{Command: "/model", Description: "Switch model and reasoning effort", Kind: "COMMAND"},
		{Command: "/permissions", Description: "Choose tool execution permissions", Kind: "COMMAND"},
		{Command: "/review", Description: "Review the current code changes", Kind: "COMMAND"},
		{Command: "/skills", Description: "Show skills", Kind: "COMMAND"},
		{Command: "/status", Description: "Show model, approvals and usage", Kind: "COMMAND"},
		{Command: "/compact", Description: "Compact conversation context", Kind: "COMMAND"},
		{Command: "/new", Description: "Start a new conversation", Kind: "COMMAND"},
		{Command: "/mcp", Description: "Show MCP tools and servers", Kind: "COMMAND"},
		{Command: "/rename", Description: "Rename the current thread", Kind: "COMMAND"},
	},
	"grok": {
		{Command: "/help", Description: "Show commands and shortcuts", Kind: "COMMAND"},
		{Command: "/new", Description: "Start a new session", Kind: "COMMAND"},
		{Command: "/resume", Description: "Resume a previous session", Kind: "COMMAND"},
		{Command: "/context", Description: "Inspect context usage", Kind: "COMMAND"},
		{Command: "/compact", Description: "Compact conversation history", Kind: "COMMAND"},
		{Command: "/skills", Description: "Show skills", Kind: "COMMAND"},
		{Command: "/mcps", Description: "Show MCP servers", Kind: "COMMAND"},
		{Command: "/rename", Description: "Rename the current session", Kind: "COMMAND"},
		{Command: "/plan", Description: "Switch to Plan mode", Kind: "COMMAND"},
		{Command: "/auto", Description: "Switch to Auto mode", Kind: "COMMAND"},
	},
}

// List returns provider built-ins plus skills/legacy commands that are actually
// present on this worker. Project-local entries take precedence over user ones.
func List(provider, projectPath string) []model.SlashItem {
	provider = strings.ToLower(strings.TrimSpace(provider))
	items := append([]model.SlashItem(nil), builtins[provider]...)
	seen := make(map[string]bool, len(items))
	for _, item := range items {
		seen[item.Command] = true
	}

	home, _ := os.UserHomeDir()
	for _, root := range skillRoots(provider, projectPath, home) {
		for _, item := range discoverSkills(root, provider) {
			if item.Command == "" || seen[item.Command] {
				continue
			}
			seen[item.Command] = true
			items = append(items, item)
		}
	}
	if provider == "claude" {
		for _, root := range []string{
			filepath.Join(projectPath, ".claude", "commands"),
			filepath.Join(home, ".claude", "commands"),
		} {
			for _, item := range discoverClaudeCommands(root) {
				if item.Command == "" || seen[item.Command] {
					continue
				}
				seen[item.Command] = true
				items = append(items, item)
			}
		}
	}

	if len(items) <= len(builtins[provider]) {
		return items
	}
	prefix := len(builtins[provider])
	sort.SliceStable(items[prefix:], func(i, j int) bool {
		return strings.ToLower(items[prefix+i].Command) < strings.ToLower(items[prefix+j].Command)
	})
	return items
}

func skillRoots(provider, projectPath, home string) []string {
	switch provider {
	case "claude":
		return []string{
			filepath.Join(projectPath, ".claude", "skills"),
			filepath.Join(home, ".claude", "skills"),
		}
	case "grok":
		return []string{
			filepath.Join(projectPath, ".grok", "skills"),
			filepath.Join(home, ".grok", "skills"),
		}
	case "codex":
		roots := []string{
			filepath.Join(projectPath, ".codex", "skills"),
			filepath.Join(projectPath, ".agents", "skills"),
			filepath.Join(home, ".codex", "skills"),
			filepath.Join(home, ".agents", "skills"),
		}
		if codexHome := strings.TrimSpace(os.Getenv("CODEX_HOME")); codexHome != "" {
			roots = append(roots, filepath.Join(codexHome, "skills"))
		}
		return roots
	default:
		return nil
	}
}

func discoverSkills(root, provider string) []model.SlashItem {
	if root == "" {
		return nil
	}
	var out []model.SlashItem
	_ = filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() {
			return nil
		}
		if !strings.EqualFold(d.Name(), "SKILL.md") {
			return nil
		}
		name, description := readFrontmatter(path)
		if name == "" {
			name = filepath.Base(filepath.Dir(path))
		}
		name = strings.TrimSpace(name)
		if name == "" {
			return nil
		}
		command := "/" + name
		if provider == "codex" {
			command = "$" + name
		}
		if description == "" {
			description = "Installed skill"
		}
		out = append(out, model.SlashItem{Command: command, Description: description, Kind: "SKILL"})
		return nil
	})
	return out
}

func discoverClaudeCommands(root string) []model.SlashItem {
	if root == "" {
		return nil
	}
	var out []model.SlashItem
	_ = filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() || !strings.EqualFold(filepath.Ext(d.Name()), ".md") {
			return nil
		}
		rel, relErr := filepath.Rel(root, path)
		if relErr != nil {
			return nil
		}
		rel = strings.TrimSuffix(filepath.ToSlash(rel), filepath.Ext(rel))
		name := strings.ReplaceAll(rel, "/", ":")
		_, description := readFrontmatter(path)
		if description == "" {
			description = "Project command"
		}
		out = append(out, model.SlashItem{Command: "/" + name, Description: description, Kind: "COMMAND"})
		return nil
	})
	return out
}

func readFrontmatter(path string) (name, description string) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", ""
	}
	lines := strings.Split(string(b), "\n")
	if len(lines) == 0 || strings.TrimSpace(lines[0]) != "---" {
		return "", ""
	}
	for i := 1; i < len(lines) && i < 60; i++ {
		line := strings.TrimSpace(lines[i])
		if line == "---" {
			break
		}
		key, value, ok := strings.Cut(line, ":")
		if !ok {
			continue
		}
		value = strings.Trim(strings.TrimSpace(value), "\"'")
		switch strings.TrimSpace(strings.ToLower(key)) {
		case "name":
			name = value
		case "description":
			description = value
		}
	}
	return name, description
}
