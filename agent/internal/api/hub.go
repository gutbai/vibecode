package api

import (
	"github.com/gutbai/vibecode/agent/internal/model"
	"sync"
)

type Hub struct {
	mu   sync.RWMutex
	subs map[chan model.Event]struct{}
}

func NewHub() *Hub { return &Hub{subs: map[chan model.Event]struct{}{}} }
func (h *Hub) Publish(e model.Event) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for ch := range h.subs {
		select {
		case ch <- e:
		default:
		}
	}
}
func (h *Hub) Subscribe() chan model.Event {
	ch := make(chan model.Event, 64)
	h.mu.Lock()
	h.subs[ch] = struct{}{}
	h.mu.Unlock()
	return ch
}
func (h *Hub) Unsubscribe(ch chan model.Event) {
	h.mu.Lock()
	if _, ok := h.subs[ch]; ok {
		delete(h.subs, ch)
		close(ch)
	}
	h.mu.Unlock()
}
