package session

import "testing"

func TestAutoSessionTitle(t *testing.T) {
	tests := []struct {
		name string
		in   string
		want string
	}{
		{name: "empty", in: "   ", want: ""},
		{name: "single line", in: "check vip71", want: "check vip71"},
		{name: "collapse whitespace", in: "fix\n  remote   file editing", want: "fix remote file editing"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := autoSessionTitle(tt.in); got != tt.want {
				t.Fatalf("autoSessionTitle(%q) = %q, want %q", tt.in, got, tt.want)
			}
		})
	}
}

func TestAutoSessionTitleTruncates(t *testing.T) {
	got := autoSessionTitle("please inspect the entire project and fix the remote editing workflow without overwriting existing file contents")
	if got == "" || len([]rune(got)) > 65 || got[len(got)-3:] != "…" {
		t.Fatalf("unexpected truncated title %q", got)
	}
}
