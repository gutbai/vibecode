package machine

import (
	"bufio"
	"os"
	"runtime"
	"strconv"
	"strings"
)

type Info struct {
	Name              string `json:"name"`
	OS                string `json:"os"`
	Arch              string `json:"arch"`
	CPUs              int    `json:"cpus"`
	MemoryTotalKB     int64  `json:"memoryTotalKb"`
	MemoryAvailableKB int64  `json:"memoryAvailableKb"`
}

func Current() Info {
	host, _ := os.Hostname()
	i := Info{Name: host, OS: runtime.GOOS, Arch: runtime.GOARCH, CPUs: runtime.NumCPU()}
	f, err := os.Open("/proc/meminfo")
	if err == nil {
		defer f.Close()
		s := bufio.NewScanner(f)
		for s.Scan() {
			parts := strings.Fields(s.Text())
			if len(parts) < 2 {
				continue
			}
			v, _ := strconv.ParseInt(parts[1], 10, 64)
			switch strings.TrimSuffix(parts[0], ":") {
			case "MemTotal":
				i.MemoryTotalKB = v
			case "MemAvailable":
				i.MemoryAvailableKB = v
			}
		}
	}
	return i
}
