package model

import "time"

type SessionStatus string

const (
	StatusStarting     SessionStatus = "STARTING"
	StatusRunning      SessionStatus = "RUNNING"
	StatusWaitingInput SessionStatus = "WAITING_INPUT"
	StatusDone         SessionStatus = "DONE"
	StatusError        SessionStatus = "ERROR"
	StatusStopped      SessionStatus = "STOPPED"
	StatusDisconnected SessionStatus = "DISCONNECTED"
)

type Project struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Path string `json:"path"`
}

type Attachment struct {
	ID           string    `json:"id"`
	SessionID    string    `json:"sessionId"`
	OriginalName string    `json:"originalName"`
	LocalPath    string    `json:"localPath"`
	MimeType     string    `json:"mimeType"`
	Size         int64     `json:"size"`
	SHA256       string    `json:"sha256"`
	CreatedAt    time.Time `json:"createdAt"`
}

type MessageRole string

const (
	RoleUser   MessageRole = "USER"
	RoleAgent  MessageRole = "AGENT"
	RoleSystem MessageRole = "SYSTEM"
)

type SessionMessage struct {
	ID          string       `json:"id"`
	SessionID   string       `json:"sessionId"`
	Role        MessageRole  `json:"role"`
	Text        string       `json:"text"`
	Attachments []Attachment `json:"attachments,omitempty"`
	CreatedAt   time.Time    `json:"createdAt"`
}

type Session struct {
	ID          string           `json:"id"`
	Title       string           `json:"title"`
	Provider    string           `json:"provider"`
	ProjectID   string           `json:"projectId"`
	ProjectName string           `json:"projectName"`
	ProjectPath string           `json:"projectPath"`
	MachineName string           `json:"machineName"`
	TMuxName    string           `json:"tmuxName"`
	Status      SessionStatus    `json:"status"`
	StartedAt   time.Time        `json:"startedAt"`
	UpdatedAt   time.Time        `json:"updatedAt"`
	LastOutput  string           `json:"lastOutput,omitempty"`
	ExitCode    *int             `json:"exitCode,omitempty"`
	Messages    []SessionMessage `json:"messages,omitempty"`
}

type Event struct {
	Type      string      `json:"type"`
	SessionID string      `json:"sessionId,omitempty"`
	Data      interface{} `json:"data,omitempty"`
	At        time.Time   `json:"at"`
}

type FileNode struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	IsDir   bool   `json:"isDir"`
	Size    int64  `json:"size"`
	ModTime string `json:"modTime"`
}

type SearchResult struct {
	FilePath   string `json:"filePath"`
	LineNumber int    `json:"lineNumber"`
	Preview    string `json:"preview"`
}
