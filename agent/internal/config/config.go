package config

import (
	"encoding/json"
	"errors"
	"os"
	"os/exec"
	"path/filepath"

	"github.com/gutbai/vibecode/agent/internal/model"
)

type Provider struct {
	Command string   `json:"command"`
	Args    []string `json:"args,omitempty"`
}

type Config struct {
	Listen      string              `json:"listen"`
	Token       string              `json:"token"`
	DataDir     string              `json:"dataDir"`
	MaxUploadMB int64               `json:"maxUploadMB"`
	Projects    []model.Project     `json:"projects"`
	Providers   map[string]Provider `json:"providers"`
}

func Load(path string) (Config, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return Config{}, err
	}
	var c Config
	if err := json.Unmarshal(b, &c); err != nil {
		return Config{}, err
	}
	if c.Listen == "" {
		c.Listen = "127.0.0.1:8787"
	}
	if c.DataDir == "" {
		c.DataDir = filepath.Join(os.Getenv("HOME"), ".vibecode")
	}
	if c.MaxUploadMB <= 0 {
		c.MaxUploadMB = 50
	}
	if c.Token == "" {
		return Config{}, errors.New("config token must not be empty")
	}
	if len(c.Projects) == 0 {
		return Config{}, errors.New("at least one project must be configured")
	}
	if c.Providers == nil {
		c.Providers = map[string]Provider{}
	}
	// Older VibeCode configs were created before Grok support existed. If the
	// CLI is already installed, expose it automatically without forcing users
	// to rewrite their existing config file.
	if _, exists := c.Providers["grok"]; !exists {
		if command, lookErr := exec.LookPath("grok"); lookErr == nil {
			c.Providers["grok"] = Provider{Command: command}
		}
	}
	if len(c.Providers) == 0 {
		return Config{}, errors.New("at least one provider must be configured")
	}
	if err := os.MkdirAll(c.DataDir, 0o700); err != nil {
		return Config{}, err
	}
	return c, nil
}

func (c Config) Project(id string) (model.Project, bool) {
	for _, p := range c.Projects {
		if p.ID == id {
			return p, true
		}
	}
	return model.Project{}, false
}
