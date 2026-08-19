package store

import (
	"testing"

	"github.com/gutbai/vibecode/agent/internal/model"
)

func TestDeleteSessionRemovesSessionAndAttachments(t *testing.T) {
	dir := t.TempDir()
	s, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.PutSession(&model.Session{ID: "s1"}); err != nil {
		t.Fatal(err)
	}
	if err := s.PutAttachment(model.Attachment{ID: "a1", SessionID: "s1"}); err != nil {
		t.Fatal(err)
	}
	if err := s.PutAttachment(model.Attachment{ID: "a2", SessionID: "other"}); err != nil {
		t.Fatal(err)
	}
	if err := s.DeleteSession("s1"); err != nil {
		t.Fatal(err)
	}
	if _, ok := s.GetSession("s1"); ok {
		t.Fatal("session still exists")
	}
	if _, ok := s.Attachments["a1"]; ok {
		t.Fatal("session attachment still exists")
	}
	if _, ok := s.Attachments["a2"]; !ok {
		t.Fatal("unrelated attachment was removed")
	}
}
