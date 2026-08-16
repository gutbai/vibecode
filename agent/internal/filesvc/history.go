package filesvc

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

const maxHistoryEntries = 20

type Revision struct {
	ID        string `json:"id"`
	SHA256    string `json:"sha256"`
	Size      int64  `json:"size"`
	CreatedAt string `json:"createdAt"`
}

type revisionMeta struct {
	Path      string `json:"path"`
	SHA256    string `json:"sha256"`
	Size      int64  `json:"size"`
	CreatedAt string `json:"createdAt"`
}

func RecordRevision(dataDir, projectID, rel string, content []byte) (Revision, error) {
	dir := historyDir(dataDir, projectID, rel)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return Revision{}, err
	}

	sha := SHA256(content)
	if latest, _ := ListRevisions(dataDir, projectID, rel); len(latest) > 0 && latest[0].SHA256 == sha {
		return latest[0], nil
	}

	now := time.Now().UTC()
	id := fmt.Sprintf("%020d-%s", now.UnixNano(), sha[:12])
	dataPath := filepath.Join(dir, id+".data")
	metaPath := filepath.Join(dir, id+".json")
	if err := os.WriteFile(dataPath, content, 0o600); err != nil {
		return Revision{}, err
	}
	meta := revisionMeta{Path: filepath.ToSlash(rel), SHA256: sha, Size: int64(len(content)), CreatedAt: now.Format(time.RFC3339Nano)}
	b, err := json.Marshal(meta)
	if err != nil {
		_ = os.Remove(dataPath)
		return Revision{}, err
	}
	if err := os.WriteFile(metaPath, b, 0o600); err != nil {
		_ = os.Remove(dataPath)
		return Revision{}, err
	}
	_ = pruneHistory(dir, maxHistoryEntries)
	return Revision{ID: id, SHA256: sha, Size: int64(len(content)), CreatedAt: meta.CreatedAt}, nil
}

func ListRevisions(dataDir, projectID, rel string) ([]Revision, error) {
	dir := historyDir(dataDir, projectID, rel)
	ents, err := os.ReadDir(dir)
	if errors.Is(err, os.ErrNotExist) {
		return []Revision{}, nil
	}
	if err != nil {
		return nil, err
	}
	out := make([]Revision, 0)
	for _, ent := range ents {
		if ent.IsDir() || !strings.HasSuffix(ent.Name(), ".json") {
			continue
		}
		id := strings.TrimSuffix(ent.Name(), ".json")
		b, err := os.ReadFile(filepath.Join(dir, ent.Name()))
		if err != nil {
			continue
		}
		var meta revisionMeta
		if json.Unmarshal(b, &meta) != nil || filepath.ToSlash(rel) != meta.Path {
			continue
		}
		out = append(out, Revision{ID: id, SHA256: meta.SHA256, Size: meta.Size, CreatedAt: meta.CreatedAt})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID > out[j].ID })
	return out, nil
}

func ReadRevision(dataDir, projectID, rel, revisionID string, maxBytes int64) ([]byte, Revision, error) {
	if !validRevisionID(revisionID) {
		return nil, Revision{}, errors.New("invalid revision id")
	}
	dir := historyDir(dataDir, projectID, rel)
	metaBytes, err := os.ReadFile(filepath.Join(dir, revisionID+".json"))
	if err != nil {
		return nil, Revision{}, err
	}
	var meta revisionMeta
	if err := json.Unmarshal(metaBytes, &meta); err != nil {
		return nil, Revision{}, err
	}
	if meta.Path != filepath.ToSlash(rel) {
		return nil, Revision{}, errors.New("revision does not belong to file")
	}
	if meta.Size > maxBytes {
		return nil, Revision{}, errors.New("revision too large")
	}
	content, err := os.ReadFile(filepath.Join(dir, revisionID+".data"))
	if err != nil {
		return nil, Revision{}, err
	}
	if SHA256(content) != meta.SHA256 {
		return nil, Revision{}, errors.New("revision checksum mismatch")
	}
	return content, Revision{ID: revisionID, SHA256: meta.SHA256, Size: meta.Size, CreatedAt: meta.CreatedAt}, nil
}

func historyDir(dataDir, projectID, rel string) string {
	projectHash := sha256.Sum256([]byte(projectID))
	pathHash := sha256.Sum256([]byte(filepath.ToSlash(rel)))
	return filepath.Join(dataDir, "file-history", hex.EncodeToString(projectHash[:8]), hex.EncodeToString(pathHash[:]))
}

func validRevisionID(id string) bool {
	parts := strings.Split(id, "-")
	if len(parts) != 2 || len(parts[0]) != 20 || len(parts[1]) != 12 {
		return false
	}
	if _, err := strconv.ParseInt(parts[0], 10, 64); err != nil {
		return false
	}
	_, err := hex.DecodeString(parts[1])
	return err == nil
}

func pruneHistory(dir string, keep int) error {
	ents, err := os.ReadDir(dir)
	if err != nil {
		return err
	}
	ids := make([]string, 0)
	for _, ent := range ents {
		if !ent.IsDir() && strings.HasSuffix(ent.Name(), ".json") {
			ids = append(ids, strings.TrimSuffix(ent.Name(), ".json"))
		}
	}
	sort.Sort(sort.Reverse(sort.StringSlice(ids)))
	for _, id := range ids[keep:] {
		_ = os.Remove(filepath.Join(dir, id+".json"))
		_ = os.Remove(filepath.Join(dir, id+".data"))
	}
	return nil
}
