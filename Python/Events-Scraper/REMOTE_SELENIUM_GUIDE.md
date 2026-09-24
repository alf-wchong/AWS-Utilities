# AWS re:Invent Scraper with Remote Selenium Container

**Best approach**: Use a containerized Selenium Chrome instance to avoid local dependencies.

## Prerequisites

You already have the container running:
```bash
$ podman ps
CONTAINER ID  IMAGE                                        COMMAND
3cddcd4995e7  docker.io/selenium/standalone-chrome:latest  /opt/bin/entry_po...
```

If not running, start it:
```bash
podman run -d --name selenium-chrome selenium/standalone-chrome:latest
```

## Setup (2 steps)

### Step 1: Install Python dependencies
```bash
pip3 install selenium beautifulsoup4 lxml
```

That's it! No browser, no chromedriver, no system dependencies needed.

### Step 2: Run the scraper
```bash
python3 aws_scraper_remote.py
```

## Usage Examples

### Basic usage (uses defaults)
```bash
python3 aws_scraper_remote.py
```

### Adjust scroll count (for faster/slower connections)
```bash
# More scrolls for slow connections
python3 aws_scraper_remote.py --max-scrolls 150

# Fewer scrolls for fast connections
python3 aws_scraper_remote.py --max-scrolls 50
```

### Adjust scroll pause timing
```bash
# Faster scrolling (less wait between scrolls)
python3 aws_scraper_remote.py --scroll-pause 0.5

# Slower scrolling (more polite)
python3 aws_scraper_remote.py --scroll-pause 2.5
```

### Custom output filenames
```bash
python3 aws_scraper_remote.py --json my_events.json --csv my_events.csv
```

### Save only JSON (skip CSV)
```bash
python3 aws_scraper_remote.py --no-csv
```

### Different Selenium container host/port
```bash
# If container is on different machine
python3 aws_scraper_remote.py --host 192.168.1.100 --port 4444

# If using non-standard port
python3 aws_scraper_remote.py --host localhost --port 4445
```

## Output

### aws_events.json
```json
[
  {
    "title": "10x or bust: How Amazon's Frontier teams ship with Kiro",
    "code": "DVT206",
    "session_type": "Breakout session",
    "level": "200",
    "time": "8:00 a.m. Monday, Nov 30",
    "location": "Caesars Forum",
    "track": "",
    "description": ""
  }
]
```

### aws_events.csv
```csv
title,code,session_type,level,time,location,track,description
"10x or bust: How Amazon's Frontier teams ship with Kiro","DVT206","Breakout session","200","8:00 a.m. Monday, Nov 30","Caesars Forum","",""
```

## Troubleshooting

### Error: "Failed to connect to Selenium container"
**Check if container is running:**
```bash
podman ps | grep selenium
```

**If not running, start it:**
```bash
podman run -d --name selenium-chrome selenium/standalone-chrome:latest
```

**If running on different host:**
```bash
# Find container IP
podman inspect selenium-chrome | grep IPAddress

# Use that IP
python3 aws_scraper_remote.py --host <IP_ADDRESS>
```

### Container keeps crashing
**Check logs:**
```bash
podman logs selenium-chrome
```

**Increase memory/swap:**
```bash
podman run -d --name selenium-chrome \
  -m 2g \
  --swap 1g \
  selenium/standalone-chrome:latest
```

### VNC Access (for debugging)
The Selenium container includes VNC for watching the browser:
```bash
# VNC is available on port 5900
# Use VNC viewer to connect to: localhost:5900
# Password is usually 'secret'
```

From your terminal:
```bash
# View logs
podman logs selenium-chrome

# Connect via VNC for visual debugging
vncviewer localhost:5900
```

## Performance Notes

- **Typical runtime**: 2-4 minutes (depends on connection)
- **Container memory**: ~1GB
- **Output size**: ~5-10 MB JSON, ~2-3 MB CSV
- **Most time spent**: Scrolling to load events

## Advantages of Remote Selenium

✅ **No local dependencies** - No need to install Chrome, ChromeDriver, or system packages  
✅ **Containerized** - Consistent environment across machines  
✅ **Reproducible** - Same image, same results  
✅ **Lightweight** - Only Python packages needed  
✅ **Debuggable** - Can connect via VNC to watch browser  
✅ **Scalable** - Can run multiple containers in parallel  
✅ **Clean** - No browser processes cluttering your system  

## Python Package Requirements

Only 3 packages needed:
```bash
pip3 install selenium beautifulsoup4 lxml
```

That's it! Everything else runs in the container.

## Advanced: Run in Parallel

```bash
# Start multiple Selenium containers
podman run -d --name selenium-chrome-1 selenium/standalone-chrome:latest
podman run -d --name selenium-chrome-2 selenium/standalone-chrome:latest

# Run scrapers in parallel
python3 aws_scraper_remote.py --host localhost --port 4444 --json events_1.json &
python3 aws_scraper_remote.py --host localhost --port 4445 --json events_2.json &

wait
```

## Stopping the Container

When you're done:
```bash
# Stop the container
podman stop selenium-chrome

# Remove it
podman rm selenium-chrome

# Or remove everything at once
podman rm -f selenium-chrome
```

## Tips

1. **Start container in background**: Already running ✓
2. **Keep container running** while you develop/test multiple times
3. **Use `--scroll-pause 1.0`** for faster scraping on good connections
4. **Monitor VNC** if scroll seems to hang - you can see what's happening
5. **Save outputs to unique filenames** when testing different parameters

## Why This Approach is Best

Compared to local installation:
- ✅ No system dependency conflicts
- ✅ No ChromeDriver version mismatches  
- ✅ Works on any Linux/Mac/Windows with Podman/Docker
- ✅ Can upgrade container independently
- ✅ Multiple instances easily (different ports)
- ✅ Clean removal (just `podman rm`)

**Recommended approach for production automation!**
