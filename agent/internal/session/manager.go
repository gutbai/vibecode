package session

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/gutbai/vibecode/agent/internal/config"
	"github.com/gutbai/vibecode/agent/internal/model"
	"github.com/gutbai/vibecode/agent/internal/store"
)

type Broadcaster interface{ Publish(model.Event) }

type Manager struct {
	cfg       config.Config
	store     *store.Store
	machine   string
	providers map[string]Provider
	out       Broadcaster
	cancel    context.CancelFunc
	mu        sync.Mutex
}

func NewManager(cfg config.Config, st *store.Store, machine string, out Broadcaster) *Manager {
	ps := map[string]Provider{}
	for k, spec := range cfg.Providers {
		ps[k] = NewTMuxProvider(spec)
	}
	return &Manager{cfg: cfg, store: st, machine: machine, providers: ps, out: out}
}

func randomID(prefix string) string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return prefix + hex.EncodeToString(b)
}

func sanitize(s string) string {
	s = strings.ToLower(s)
	var b strings.Builder
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' {
			b.WriteRune(r)
		}
	}
	if b.Len() == 0 {
		return "session"
	}
	return b.String()
}

func (m *Manager) Start(ctx context.Context, provider, projectID, title, initialPrompt string) (*model.Session, error) {
	p, ok := m.providers[provider]
	if !ok {
		return nil, fmt.Errorf("unknown provider %q", provider)
	}
	project, ok := m.cfg.Project(projectID)
	if !ok {
		return nil, fmt.Errorf("unknown project %q", projectID)
	}
	if _, err := os.Stat(project.Path); err != nil {
		return nil, fmt.Errorf("project path: %w", err)
	}
	id := randomID("sess_")
	tmuxName := "vibecode-" + sanitize(provider) + "-" + id[len(id)-8:]
	now := time.Now().UTC()
	s := &model.Session{ID: id, Title: title, Provider: provider, ProjectID: project.ID, ProjectName: project.Name, ProjectPath: project.Path, MachineName: m.machine, TMuxName: tmuxName, Status: model.StatusStarting, StartedAt: now, UpdatedAt: now}
	if err := p.Start(ctx, tmuxName, project.Path); err != nil {
		return nil, err
	}
	s.Status = model.StatusRunning
	_ = m.store.PutSession(s)
	m.publish("session.created", s.ID, s)
	if strings.TrimSpace(initialPrompt) != "" {
		_, err := m.SendMessage(ctx, s.ID, initialPrompt, nil)
		if err != nil {
			return s, err
		}
	}
	return s, nil
}

func (m *Manager) Get(id string) (*model.Session, bool) { return m.store.GetSession(id) }
func (m *Manager) List() []*model.Session               { return m.store.ListSessions() }

func (m *Manager) SendMessage(ctx context.Context, sessionID, text string, attachments []model.Attachment) (*model.SessionMessage, error) {
	s, ok := m.store.GetSession(sessionID)
	if !ok {
		return nil, os.ErrNotExist
	}
	p := m.providers[s.Provider]
	if !p.Exists(ctx, s.TMuxName) {
		return nil, errors.New("session is not running")
	}
	var b strings.Builder
	b.WriteString(strings.TrimSpace(text))
	if len(attachments) > 0 {
		if b.Len() > 0 {
			b.WriteString("\n\n")
		}
		b.WriteString("Attached files on this machine:\n")
		for _, a := range attachments {
			b.WriteString("- ")
			b.WriteString(a.LocalPath)
			b.WriteByte('\n')
		}
	}
	if err := p.Send(ctx, s.TMuxName, b.String()); err != nil {
		return nil, err
	}
	msg := model.SessionMessage{ID: randomID("msg_"), SessionID: s.ID, Role: model.RoleUser, Text: text, Attachments: attachments, CreatedAt: time.Now().UTC()}
	_ = m.store.AddMessage(s.ID, msg)
	s.Status = model.StatusRunning
	s.UpdatedAt = time.Now().UTC()
	_ = m.store.PutSession(s)
	m.publish("session.message", s.ID, msg)
	m.publish("session.status_changed", s.ID, s.Status)
	return &msg, nil
}

func (m *Manager) Stop(ctx context.Context, id string) error {
	s, ok := m.store.GetSession(id)
	if !ok {
		return os.ErrNotExist
	}
	if p := m.providers[s.Provider]; p != nil && p.Exists(ctx, s.TMuxName) {
		_ = p.Stop(ctx, s.TMuxName)
	}
	s.Status = model.StatusStopped
	s.UpdatedAt = time.Now().UTC()
	_ = m.store.PutSession(s)
	m.publish("session.status_changed", s.ID, s.Status)
	return nil
}

func (m *Manager) SaveAttachment(sessionID, originalName, mime string, size int64, sha string, srcTemp string) (model.Attachment, error) {
	if _, ok := m.store.GetSession(sessionID); !ok {
		return model.Attachment{}, os.ErrNotExist
	}
	id := randomID("att_")
	dir := filepath.Join(m.cfg.DataDir, "sessions", sessionID, "attachments")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return model.Attachment{}, err
	}
	safeName := filepath.Base(originalName)
	if safeName == "." || safeName == "" {
		safeName = id
	}
	dst := filepath.Join(dir, id+"-"+safeName)
	if err := os.Rename(srcTemp, dst); err != nil {
		return model.Attachment{}, err
	}
	a := model.Attachment{ID: id, SessionID: sessionID, OriginalName: originalName, LocalPath: dst, MimeType: mime, Size: size, SHA256: sha, CreatedAt: time.Now().UTC()}
	if err := m.store.PutAttachment(a); err != nil {
		return model.Attachment{}, err
	}
	return a, nil
}

func (m *Manager) ResolveAttachments(sessionID string, ids []string) ([]model.Attachment, error) {
	return m.store.ResolveAttachments(sessionID, ids)
}

func (m *Manager) StartMonitoring(parent context.Context) {
	ctx, cancel := context.WithCancel(parent)
	m.cancel = cancel
	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				m.poll(ctx)
			}
		}
	}()
}

func (m *Manager) Close() {
	if m.cancel != nil {
		m.cancel()
	}
}

func (m *Manager) poll(ctx context.Context) {
	for _, s := range m.store.ListSessions() {
		if s.Status == model.StatusStopped || s.Status == model.StatusDone {
			continue
		}
		p := m.providers[s.Provider]
		if p == nil {
			continue
		}
		exists := p.Exists(ctx, s.TMuxName)
		out, _ := p.Capture(ctx, s.TMuxName, 120)
		st := inferStatus(out, exists)
		// A prompt marker remains in tmux scrollback for a moment after the user replies.
		// Keep RUNNING briefly so an old question does not immediately flip back to WAITING_INPUT.
		if st == model.StatusWaitingInput && s.Status == model.StatusRunning && time.Since(s.UpdatedAt) < 4*time.Second {
			st = model.StatusRunning
		}
		changed := st != s.Status || (out != "" && out != s.LastOutput)
		if !changed {
			continue
		}
		old := s.Status
		s.Status = st
		s.LastOutput = out
		s.UpdatedAt = time.Now().UTC()
		_ = m.store.PutSession(s)
		if out != "" {
			m.publish("session.output", s.ID, map[string]string{"text": out})
		}
		if old != st {
			m.publish("session.status_changed", s.ID, st)
		}
	}
}

func (m *Manager) publish(t, id string, data interface{}) {
	if m.out != nil {
		m.out.Publish(model.Event{Type: t, SessionID: id, Data: data, At: time.Now().UTC()})
	}
}
