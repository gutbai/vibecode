package api

import (
    "net/http"
    "strconv"

    "github.com/gutbai/vibecode/agent/internal/logbuf"
)

func (s *Server) logs(w http.ResponseWriter, r *http.Request) {
    limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
    jsonOut(w, http.StatusOK, logbuf.List(limit))
}
