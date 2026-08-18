package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/gutbai/vibecode/agent/internal/config"
	"github.com/gutbai/vibecode/agent/internal/filesvc"
	"github.com/gutbai/vibecode/agent/internal/gitsvc"
	"github.com/gutbai/vibecode/agent/internal/machine"
	"github.com/gutbai/vibecode/agent/internal/model"
	"github.com/gutbai/vibecode/agent/internal/searchsvc"
	"github.com/gutbai/vibecode/agent/internal/session"
	"github.com/gutbai/vibecode/agent/internal/slashsvc"
)

const maxEditableFileBytes int64 = 2 * 1024 * 1024

type Server struct {
	cfg      config.Config
	sessions *session.Manager
	hub      *Hub
	machine  machine.Info
	http     *http.Server
}

func New(cfg config.Config, sm *session.Manager, hub *Hub) *Server {
	s := &Server{cfg: cfg, sessions: sm, hub: hub, machine: machine.Current()}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", s.health)
	mux.HandleFunc("GET /api/machine", s.auth(s.machineInfo))
	mux.HandleFunc("GET /api/projects", s.auth(s.projects))
	mux.HandleFunc("GET /api/sessions", s.auth(s.listSessions))
	mux.HandleFunc("POST /api/sessions", s.auth(s.createSession))
	mux.HandleFunc("GET /api/sessions/{id}", s.auth(s.getSession))
	mux.HandleFunc("POST /api/sessions/{id}/messages", s.auth(s.sendMessage))
	mux.HandleFunc("POST /api/sessions/{id}/keys", s.auth(s.sendKeys))
	mux.HandleFunc("POST /api/sessions/{id}/attachments", s.auth(s.uploadAttachment))
	mux.HandleFunc("POST /api/sessions/{id}/stop", s.auth(s.stopSession))
	mux.HandleFunc("GET /api/projects/{id}/files", s.auth(s.listFiles))
	mux.HandleFunc("GET /api/projects/{id}/file", s.auth(s.readFile))
	mux.HandleFunc("PUT /api/projects/{id}/file", s.auth(s.writeFile))
	mux.HandleFunc("GET /api/projects/{id}/file/history", s.auth(s.fileHistory))
	mux.HandleFunc("GET /api/projects/{id}/file/revision", s.auth(s.readFileRevision))
	mux.HandleFunc("POST /api/projects/{id}/file/restore", s.auth(s.restoreFileRevision))
	mux.HandleFunc("GET /api/projects/{id}/search", s.auth(s.search))
	mux.HandleFunc("GET /api/projects/{id}/slash", s.auth(s.slash))
	mux.HandleFunc("GET /api/projects/{id}/git/status", s.auth(s.gitStatus))
	mux.HandleFunc("GET /api/projects/{id}/git/diff", s.auth(s.gitDiff))
	mux.HandleFunc("GET /api/projects/{id}/git/log", s.auth(s.gitLog))
	mux.HandleFunc("GET /ws", s.ws)
	s.http = &http.Server{Addr: cfg.Listen, Handler: logging(mux), ReadHeaderTimeout: 10 * time.Second}
	return s
}

func (s *Server) ListenAndServe() error              { return s.http.ListenAndServe() }
func (s *Server) Shutdown(ctx context.Context) error { return s.http.Shutdown(ctx) }
func jsonOut(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func errOut(w http.ResponseWriter, status int, err error) {
	jsonOut(w, status, map[string]string{"error": err.Error()})
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if token == "" {
			token = r.URL.Query().Get("token")
		}
		if token != s.cfg.Token {
			errOut(w, http.StatusUnauthorized, fmt.Errorf("unauthorized"))
			return
		}
		next(w, r)
	}
}
func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { next.ServeHTTP(w, r) })
}
func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	jsonOut(w, 200, map[string]interface{}{"ok": true, "time": time.Now().UTC()})
}
func (s *Server) machineInfo(w http.ResponseWriter, r *http.Request) { jsonOut(w, 200, s.machine) }
func (s *Server) projects(w http.ResponseWriter, r *http.Request)    { jsonOut(w, 200, s.cfg.Projects) }
func (s *Server) listSessions(w http.ResponseWriter, r *http.Request) {
	jsonOut(w, 200, s.sessions.List())
}
func (s *Server) getSession(w http.ResponseWriter, r *http.Request) {
	v, ok := s.sessions.Get(r.PathValue("id"))
	if !ok {
		errOut(w, 404, os.ErrNotExist)
		return
	}
	jsonOut(w, 200, v)
}

func (s *Server) createSession(w http.ResponseWriter, r *http.Request) {
	var in struct{ Provider, ProjectID, Title, Prompt string }
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		errOut(w, 400, err)
		return
	}
	v, err := s.sessions.Start(r.Context(), in.Provider, in.ProjectID, in.Title, in.Prompt)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 201, v)
}
func (s *Server) sendMessage(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Text          string   `json:"text"`
		AttachmentIDs []string `json:"attachmentIds"`
	}
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		errOut(w, 400, err)
		return
	}
	attachments, err := s.sessions.ResolveAttachments(r.PathValue("id"), in.AttachmentIDs)
	if err != nil {
		errOut(w, 400, fmt.Errorf("invalid attachment reference"))
		return
	}
	v, err := s.sessions.SendMessage(r.Context(), r.PathValue("id"), in.Text, attachments)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 201, v)
}
func (s *Server) sendKeys(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Keys []string `json:"keys"`
	}
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		errOut(w, 400, err)
		return
	}
	if err := s.sessions.SendControlKeys(r.Context(), r.PathValue("id"), in.Keys); err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]bool{"ok": true})
}
func (s *Server) stopSession(w http.ResponseWriter, r *http.Request) {
	if err := s.sessions.Stop(r.Context(), r.PathValue("id")); err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]bool{"ok": true})
}

func writeTemp(file multipart.File, max int64) (string, int64, string, error) {
	f, err := os.CreateTemp("", "vibecode-upload-*")
	if err != nil {
		return "", 0, "", err
	}
	name := f.Name()
	h := sha256.New()
	n, err := io.Copy(io.MultiWriter(f, h), io.LimitReader(file, max+1))
	cerr := f.Close()
	if err == nil {
		err = cerr
	}
	if err != nil {
		os.Remove(name)
		return "", 0, "", err
	}
	if n > max {
		os.Remove(name)
		return "", 0, "", fmt.Errorf("file exceeds upload limit")
	}
	return name, n, hex.EncodeToString(h.Sum(nil)), nil
}
func (s *Server) uploadAttachment(w http.ResponseWriter, r *http.Request) {
	max := s.cfg.MaxUploadMB * 1024 * 1024
	r.Body = http.MaxBytesReader(w, r.Body, max+1024*1024)
	if err := r.ParseMultipartForm(max); err != nil {
		errOut(w, 400, err)
		return
	}
	file, hdr, err := r.FormFile("file")
	if err != nil {
		errOut(w, 400, err)
		return
	}
	defer file.Close()
	tmp, size, sha, err := writeTemp(file, max)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	mime := hdr.Header.Get("Content-Type")
	a, err := s.sessions.SaveAttachment(r.PathValue("id"), hdr.Filename, mime, size, sha, tmp)
	if err != nil {
		os.Remove(tmp)
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 201, a)
}

func (s *Server) project(id string) (model.Project, error) {
	p, ok := s.cfg.Project(id)
	if !ok {
		return model.Project{}, os.ErrNotExist
	}
	return p, nil
}
func (s *Server) listFiles(w http.ResponseWriter, r *http.Request) {
	p, err := s.project(r.PathValue("id"))
	if err != nil {
		errOut(w, 404, err)
		return
	}
	nodes, err := filesvc.List(p.Path, r.URL.Query().Get("path"))
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, nodes)
}
func (s *Server) readFile(w http.ResponseWriter, r *http.Request) {
	p, err := s.project(r.PathValue("id"))
	if err != nil {
		errOut(w, 404, err)
		return
	}
	rel := r.URL.Query().Get("path")
	b, err := filesvc.Read(p.Path, rel, maxEditableFileBytes)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]string{
		"path":    filepath.ToSlash(rel),
		"content": string(b),
		"sha256":  filesvc.SHA256(b),
	})
}
func (s *Server) writeFile(w http.ResponseWriter, r *http.Request) {
	projectID := r.PathValue("id")
	p, err := s.project(projectID)
	if err != nil {
		errOut(w, 404, err)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxEditableFileBytes*8)
	var in struct {
		Path           string `json:"path"`
		Content        string `json:"content"`
		ExpectedSHA256 string `json:"expectedSha256"`
	}
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		errOut(w, 400, err)
		return
	}
	current, err := filesvc.Read(p.Path, in.Path, maxEditableFileBytes)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	if in.ExpectedSHA256 != "" && !strings.EqualFold(filesvc.SHA256(current), in.ExpectedSHA256) {
		errOut(w, http.StatusConflict, filesvc.ErrContentChanged)
		return
	}
	if _, err := filesvc.RecordRevision(s.cfg.DataDir, projectID, in.Path, current); err != nil {
		errOut(w, 500, fmt.Errorf("record file history: %w", err))
		return
	}
	content := []byte(in.Content)
	if err := filesvc.Write(p.Path, in.Path, content, maxEditableFileBytes, in.ExpectedSHA256); err != nil {
		if errors.Is(err, filesvc.ErrContentChanged) {
			errOut(w, http.StatusConflict, err)
			return
		}
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]interface{}{
		"ok":     true,
		"path":   filepath.ToSlash(in.Path),
		"sha256": filesvc.SHA256(content),
	})
}
func (s *Server) fileHistory(w http.ResponseWriter, r *http.Request) {
	projectID := r.PathValue("id")
	p, err := s.project(projectID)
	if err != nil {
		errOut(w, 404, err)
		return
	}
	rel := r.URL.Query().Get("path")
	if _, err := filesvc.Read(p.Path, rel, maxEditableFileBytes); err != nil {
		errOut(w, 400, err)
		return
	}
	revisions, err := filesvc.ListRevisions(s.cfg.DataDir, projectID, rel)
	if err != nil {
		errOut(w, 500, err)
		return
	}
	jsonOut(w, 200, revisions)
}
func (s *Server) readFileRevision(w http.ResponseWriter, r *http.Request) {
	projectID := r.PathValue("id")
	p, err := s.project(projectID)
	if err != nil {
		errOut(w, 404, err)
		return
	}
	rel := r.URL.Query().Get("path")
	if _, err := filesvc.Read(p.Path, rel, maxEditableFileBytes); err != nil {
		errOut(w, 400, err)
		return
	}
	content, revision, err := filesvc.ReadRevision(s.cfg.DataDir, projectID, rel, r.URL.Query().Get("revisionId"), maxEditableFileBytes)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]interface{}{
		"path":      filepath.ToSlash(rel),
		"content":   string(content),
		"revision":  revision,
		"sha256":    revision.SHA256,
		"createdAt": revision.CreatedAt,
	})
}
func (s *Server) restoreFileRevision(w http.ResponseWriter, r *http.Request) {
	projectID := r.PathValue("id")
	p, err := s.project(projectID)
	if err != nil {
		errOut(w, 404, err)
		return
	}
	var in struct {
		Path           string `json:"path"`
		RevisionID     string `json:"revisionId"`
		ExpectedSHA256 string `json:"expectedSha256"`
	}
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		errOut(w, 400, err)
		return
	}
	current, err := filesvc.Read(p.Path, in.Path, maxEditableFileBytes)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	currentSHA := filesvc.SHA256(current)
	if in.ExpectedSHA256 != "" && !strings.EqualFold(currentSHA, in.ExpectedSHA256) {
		errOut(w, http.StatusConflict, filesvc.ErrContentChanged)
		return
	}
	restored, _, err := filesvc.ReadRevision(s.cfg.DataDir, projectID, in.Path, in.RevisionID, maxEditableFileBytes)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	if _, err := filesvc.RecordRevision(s.cfg.DataDir, projectID, in.Path, current); err != nil {
		errOut(w, 500, fmt.Errorf("record file history: %w", err))
		return
	}
	if err := filesvc.Write(p.Path, in.Path, restored, maxEditableFileBytes, currentSHA); err != nil {
		if errors.Is(err, filesvc.ErrContentChanged) {
			errOut(w, http.StatusConflict, err)
			return
		}
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]interface{}{
		"ok":      true,
		"path":    filepath.ToSlash(in.Path),
		"content": string(restored),
		"sha256":  filesvc.SHA256(restored),
	})
}
func (s *Server) search(w http.ResponseWriter, r *http.Request) {
	p, err := s.project(r.PathValue("id"))
	if err != nil {
		errOut(w, 404, err)
		return
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	v, err := searchsvc.Search(r.Context(), p.Path, r.URL.Query().Get("q"), limit)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, v)
}
func (s *Server) slash(w http.ResponseWriter, r *http.Request) {
	p, err := s.project(r.PathValue("id"))
	if err != nil {
		errOut(w, 404, err)
		return
	}
	provider := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("provider")))
	if _, ok := s.cfg.Providers[provider]; !ok {
		errOut(w, 400, fmt.Errorf("unknown provider %q", provider))
		return
	}
	jsonOut(w, 200, slashsvc.List(provider, p.Path))
}
func (s *Server) gitStatus(w http.ResponseWriter, r *http.Request) { s.gitCommand(w, r, gitsvc.Status) }
func (s *Server) gitDiff(w http.ResponseWriter, r *http.Request)   { s.gitCommand(w, r, gitsvc.Diff) }
func (s *Server) gitLog(w http.ResponseWriter, r *http.Request)    { s.gitCommand(w, r, gitsvc.Log) }
func (s *Server) gitCommand(w http.ResponseWriter, r *http.Request, fn func(context.Context, string) (string, error)) {
	p, err := s.project(r.PathValue("id"))
	if err != nil {
		errOut(w, 404, err)
		return
	}
	out, err := fn(r.Context(), p.Path)
	if err != nil {
		errOut(w, 400, err)
		return
	}
	jsonOut(w, 200, map[string]string{"output": out})
}

var upgrader = websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

func (s *Server) ws(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	if token != s.cfg.Token {
		errOut(w, 401, fmt.Errorf("unauthorized"))
		return
	}
	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer c.Close()
	ch := s.hub.Subscribe()
	defer s.hub.Unsubscribe(ch)
	for e := range ch {
		if err := c.WriteJSON(e); err != nil {
			return
		}
	}
}
