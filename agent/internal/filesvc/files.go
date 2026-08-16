package filesvc

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/gutbai/vibecode/agent/internal/model"
)

func SafeJoin(root, rel string) (string, error) {
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	for _, part := range strings.FieldsFunc(filepath.ToSlash(rel), func(r rune) bool { return r == '/' }) {
		if part == ".." {
			return "", errors.New("path escapes project root")
		}
	}
	clean := filepath.Clean("/" + rel)
	target := filepath.Join(rootAbs, strings.TrimPrefix(clean, "/"))
	targetAbs, err := filepath.Abs(target)
	if err != nil {
		return "", err
	}
	if targetAbs != rootAbs && !strings.HasPrefix(targetAbs, rootAbs+string(os.PathSeparator)) {
		return "", errors.New("path escapes project root")
	}
	return targetAbs, nil
}

func List(root, rel string) ([]model.FileNode, error) {
	p, err := SafeJoin(root, rel)
	if err != nil {
		return nil, err
	}
	ents, err := os.ReadDir(p)
	if err != nil {
		return nil, err
	}
	out := make([]model.FileNode, 0, len(ents))
	for _, e := range ents {
		info, err := e.Info()
		if err != nil {
			continue
		}
		child := filepath.ToSlash(filepath.Join(rel, e.Name()))
		out = append(out, model.FileNode{Name: e.Name(), Path: child, IsDir: e.IsDir(), Size: info.Size(), ModTime: info.ModTime().UTC().Format("2006-01-02T15:04:05Z")})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].IsDir != out[j].IsDir {
			return out[i].IsDir
		}
		return strings.ToLower(out[i].Name) < strings.ToLower(out[j].Name)
	})
	return out, nil
}

func Read(root, rel string, maxBytes int64) ([]byte, error) {
	p, err := SafeJoin(root, rel)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(p)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return nil, err
	}
	if st.IsDir() {
		return nil, errors.New("path is a directory")
	}
	if st.Size() > maxBytes {
		return nil, errors.New("file too large to preview")
	}
	return os.ReadFile(p)
}

func Write(root, rel string, content []byte, maxBytes int64) error {
	if strings.TrimSpace(rel) == "" {
		return errors.New("file path is required")
	}
	if int64(len(content)) > maxBytes {
		return fmt.Errorf("file exceeds edit limit of %d bytes", maxBytes)
	}

	p, err := SafeJoin(root, rel)
	if err != nil {
		return err
	}
	p, err = resolveExistingInsideRoot(root, p)
	if err != nil {
		return err
	}

	st, err := os.Stat(p)
	if err != nil {
		return err
	}
	if st.IsDir() {
		return errors.New("path is a directory")
	}
	if !st.Mode().IsRegular() {
		return errors.New("only regular files can be edited")
	}

	tmp, err := os.CreateTemp(filepath.Dir(p), ".vibecode-edit-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	keepTemp := true
	defer func() {
		if keepTemp {
			_ = os.Remove(tmpName)
		}
	}()

	if err := tmp.Chmod(st.Mode().Perm()); err != nil {
		_ = tmp.Close()
		return err
	}
	if _, err := tmp.Write(content); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmpName, p); err != nil {
		return err
	}
	keepTemp = false
	return nil
}

func resolveExistingInsideRoot(root, target string) (string, error) {
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	rootReal, err := filepath.EvalSymlinks(rootAbs)
	if err != nil {
		return "", err
	}
	targetReal, err := filepath.EvalSymlinks(target)
	if err != nil {
		return "", err
	}
	if targetReal != rootReal && !strings.HasPrefix(targetReal, rootReal+string(os.PathSeparator)) {
		return "", errors.New("path escapes project root through symlink")
	}
	return targetReal, nil
}
