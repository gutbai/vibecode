package main

import (
	"context"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gutbai/vibecode/agent/internal/api"
	"github.com/gutbai/vibecode/agent/internal/config"
	"github.com/gutbai/vibecode/agent/internal/machine"
	"github.com/gutbai/vibecode/agent/internal/session"
	"github.com/gutbai/vibecode/agent/internal/store"
)

func main() {
	cfgPath := flag.String("config", "config.json", "path to config JSON")
	flag.Parse()
	cfg, err := config.Load(*cfgPath)
	if err != nil {
		log.Fatal(err)
	}
	st, err := store.Open(cfg.DataDir)
	if err != nil {
		log.Fatal(err)
	}
	hub := api.NewHub()
	mi := machine.Current()
	sm := session.NewManager(cfg, st, mi.Name, hub)
	sm.StartMonitoring(context.Background())
	defer sm.Close()
	srv := api.New(cfg, sm, hub)
	go func() {
		log.Printf("VibeCode Agent listening on %s", cfg.Listen)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal(err)
		}
	}()
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}
