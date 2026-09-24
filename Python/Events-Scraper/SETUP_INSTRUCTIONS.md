# Setup Instructions for Your Fedora System

You're using **podman with a remote Selenium container** — the cleanest approach!

## Prerequisites Check

Verify you have:
```bash
# Check container is running
podman ps | grep selenium
# Should show: selenium/standalone-chrome:latest

# If not running:
podman run -d --name selenium-chrome selenium/standalone-chrome:latest
```

## One-Time Setup (5 minutes)

### Step 1: Copy the scraper script to your machine
Download `aws_scraper_remote.py` to your Fedora system.

### Step 2: Install Python packages
On your Fedora system (you may need `sudo` or `--break-system-packages`):

```bash
# Option A: System-wide (on Fedora)
pip3 install --break-system-packages selenium beautifulsoup4 lxml

# Option B: Virtual environment (recommended)
python3 -m venv aws_scraper_env
source aws_scraper_env/bin/activate
pip3 install selenium beautifulsoup4 lxml
```

## Run the Scraper

### From your Fedora system:

```bash
# Activate venv if you used it
source aws_scraper_env/bin/activate

# Run the scraper (connects to podman container)
python3 aws_scraper_remote.py
```

### Output
```
aws_events.json   (all event data as JSON)
aws_events.csv    (spreadsheet-friendly format)
```

## That's It!

The scraper will:
1. ✅ Connect to your running Selenium container
2. ✅ Navigate to AWS event catalog
3. ✅ Scroll through entire page (load all events)
4. ✅ Parse event information
5. ✅ Save to JSON and CSV
6. ✅ Print summary statistics

**Total time: ~3-4 minutes**

## Usage Examples

```bash
# Basic run
python3 aws_scraper_remote.py

# Faster (fewer scrolls on good connection)
python3 aws_scraper_remote.py --max-scrolls 50

# More conservative (slower, more polite)
python3 aws_scraper_remote.py --max-scrolls 150 --scroll-pause 2

# Custom output
python3 aws_scraper_remote.py \
  --json reinvent_2026_events.json \
  --csv reinvent_2026_events.csv
```

## File Contents

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
  },
  ...
]
```

### aws_events.csv
```csv
title,code,session_type,level,time,location,track,description
"10x or bust: How Amazon's Frontier teams ship with Kiro","DVT206","Breakout session","200","8:00 a.m. Monday, Nov 30","Caesars Forum","",""
```

## Troubleshooting

### "Failed to connect to Selenium container"
```bash
# Check if container is running
podman ps | grep selenium

# If not, start it
podman run -d --name selenium-chrome selenium/standalone-chrome:latest

# Verify it's accessible
podman port selenium-chrome
# Should show 4444/tcp
```

### "ModuleNotFoundError: No module named 'selenium'"
```bash
# Install packages (choose one method):

# Method 1: System-wide
pip3 install --break-system-packages selenium beautifulsoup4 lxml

# Method 2: Virtual environment
python3 -m venv myenv
source myenv/bin/activate
pip3 install selenium beautifulsoup4 lxml
python3 aws_scraper_remote.py
```

### "Timeout during page load"
The page might be slow. Increase wait time in the script or use:
```bash
python3 aws_scraper_remote.py --scroll-pause 2.5
```

### "No events found"
The HTML structure might have changed. Enable debugging:
- Check the page manually: https://registration.awsevents.com/flow/awsevents/reinvent2026/eventcatalog/page/eventcatalog?search=
- Connect to VNC to see what the browser is doing:
  ```bash
  vncviewer localhost:5900
  # Password: secret
  ```

## Requirements Summary

**What you need on your Fedora system:**
- ✅ Python 3.7+ (probably already installed)
- ✅ pip3 (package manager)
- ✅ `selenium` package
- ✅ `beautifulsoup4` package
- ✅ `lxml` package

**What you DON'T need:**
- ❌ Chrome browser
- ❌ ChromeDriver
- ❌ Chromium browser
- ❌ Any system dependencies

Everything runs in the **podman container**!

## Checking Python Installation

```bash
# Check Python version
python3 --version
# Should be 3.7+

# Check pip
pip3 --version

# Check which Python
which python3
```

## Advanced Options

All command-line arguments:
```bash
python3 aws_scraper_remote.py --help

# Output:
# usage: aws_scraper_remote.py [-h] [--host HOST] [--port PORT] 
#                              [--max-scrolls MAX_SCROLLS]
#                              [--scroll-pause SCROLL_PAUSE]
#                              [--json JSON] [--csv CSV]
#                              [--no-csv] [--no-json]
```

### Examples:
```bash
# Different Selenium host/port
python3 aws_scraper_remote.py --host 192.168.1.100 --port 4444

# Only JSON, no CSV
python3 aws_scraper_remote.py --no-csv

# Only CSV, no JSON
python3 aws_scraper_remote.py --no-json

# Different output locations
python3 aws_scraper_remote.py --json /tmp/events.json --csv /tmp/events.csv

# Aggressive scrolling (fewer scrolls)
python3 aws_scraper_remote.py --max-scrolls 30 --scroll-pause 0.5
```

## Monitoring Execution

### Watch the browser live (VNC)
```bash
# From another terminal
vncviewer localhost:5900
# Password: secret
```

### View container logs
```bash
podman logs selenium-chrome
podman logs -f selenium-chrome  # Follow logs
```

## When You're Done

```bash
# The container can stay running for next time
# Or stop it to save resources:
podman stop selenium-chrome

# Remove it entirely:
podman rm selenium-chrome

# Update to latest image:
podman pull selenium/standalone-chrome:latest
```

## Next Steps

1. **Copy `aws_scraper_remote.py`** to your Fedora system
2. **Install packages**: `pip3 install selenium beautifulsoup4 lxml`
3. **Run**: `python3 aws_scraper_remote.py`
4. **Check output**: `ls -lh aws_events.*`

**Questions?** Check `REMOTE_SELENIUM_GUIDE.md` for detailed docs.

---

**This approach is production-ready and recommended for automation!** ✅
