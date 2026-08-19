package store

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"sync"

	"github.com/gutbai/vibecode/agent/internal/model"
)

type Store struct {
	mu          sync.RWMutex
	path        string
	Sessions    map[string]*model.Session   `json:"sessions"`
	Attachments map[string]model.Attachment `json:"attachments"`
}

func Open(dataDir string) (*Store, error) {
	s := &Store{path: filepath.Join(dataDir, "state.json"), Sessions: map[string]*model.Session{}, Attachments: map[string]model.Attachment{}}
	b, err := os.ReadFile(s.path)
	if err == nil {
		_ = json.Unmarshal(b, s)
		if s.Sessions == nil {
			s.Sessions = map[string]*model.Session{}
		}
		if s.Attachments == nil {
			s.Attachments = map[string]model.Attachment{}
		}
	}
	if os.IsNotExist(err) {
		return s, nil
	}
	return s, err
}

func (s *Store) persistLocked() error {
	b, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

func (s *Store) PutSession(v *model.Session) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	cp := *v
	s.Sessions[v.ID] = &cp
	return s.persistLocked()
}

func (s *Store) GetSession(id string) (*model.Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	v, ok := s.Sessions[id]
	if !ok {
		return nil, false
	}
	cp := *v
	cp.Messages = append([]model.SessionMessage(nil), v.Messages...)
	return &cp, true
}

func (s *Store) ListSessions() []*model.Session {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]*model.Session, 0, len(s.Sessions))
	for _, v := range s.Sessions {
		cp := *v
		cp.Messages = nil
		out = append(out, &cp)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].UpdatedAt.After(out[j].UpdatedAt) })
	return out
}

func (s *Store) DeleteSession(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.Sessions[id]; !ok {
		return os.ErrNotExist
	}
	delete(s.Sessions, id)
	for attachmentID, attachment := range s.Attachments {
		if attachment.SessionID == id {
			delete(s.Attachments, attachmentID)
		}
	}
	return s.persistLocked()
}

func (s *Store) AddMessage(sessionID string, m model.SessionMessage) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if v, ok := s.Sessions[sessionID]; ok {
		v.Messages = append(v.Messages, m)
		return s.persistLocked()
	}
	return os.ErrNotExist
}

func (s *Store) PutAttachment(a model.Attachment) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.Attachments[a.ID] = a
	return s.persistLocked()
}

func (s *Store) ResolveAttachments(sessionID string, ids []string) ([]model.Attachment, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]model.Attachment, 0, len(ids))
	for _, id := range ids {
		a, ok := s.Attachments[id]
		if !ok || a.SessionID != sessionID {
			return nil, os.ErrNotExist
		}
		out = append(out, a)
	}
	return out, nil
}
