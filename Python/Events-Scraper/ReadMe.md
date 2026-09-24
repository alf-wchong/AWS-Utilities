# 🚀 AWS re:Invent 2026 Event Scraper - START HERE

Welcome! You have everything needed to scrape the AWS re:Invent 2026 event catalog using a **remote Selenium container**.

---

## ⚡ 30-Second Quick Start

```bash
# 1. Install Python packages (one time)
pip3 install selenium beautifulsoup4 lxml

# 2. Verify container is running
podman ps | grep selenium

# 3. Run the scraper
python3 aws_scraper_remote.py

# 4. Check output
ls -lh aws_events.*
```

**Done!** ✅ You now have `aws_events.json` and `aws_events.csv`

---

## 📚 Documentation

Read based on your needs:

| Time | File | Why Read |
|------|------|----------|
| **1 min** | [ARCHITECTURE.txt](ARCHITECTURE.txt) | Visual overview of how it works |
| **3 min** | [QUICK_START.md](QUICK_START.md) | Commands and basic usage |
| **10 min** | [SETUP_INSTRUCTIONS.md](SETUP_INSTRUCTIONS.md) | Step-by-step setup guide |
| **15 min** | [REMOTE_SELENIUM_GUIDE.md](REMOTE_SELENIUM_GUIDE.md) | Detailed documentation |
| **5 min** | [FILES_SUMMARY.md](FILES_SUMMARY.md) | Overview of all files |

---

## 📦 What You Have

### Files Included
- **aws_scraper_remote.py** ← The scraper (15 KB)
- **QUICK_START.md** ← Quick reference
- **SETUP_INSTRUCTIONS.md** ← Detailed setup
- **REMOTE_SELENIUM_GUIDE.md** ← Full documentation  
- **ARCHITECTURE.txt** ← How it works (with diagrams)
- **FILES_SUMMARY.md** ← Overview of everything
- **START_HERE.md** ← This file

### What's Already Running
- ✅ Podman installed
- ✅ Selenium Chrome container running
  ```bash
  $ podman ps | grep selenium
  3cddcd4995e7  selenium/standalone-chrome:latest  Up 20 seconds
  ```

### What You Need to Install
- ✅ Python 3.7+
- ✅ `selenium` package
- ✅ `beautifulsoup4` package
- ✅ `lxml` package

That's it!

---

## 🎯 Your Task: 4 Simple Steps

### Step 1: Install Python Packages
On your Fedora system:
```bash
pip3 install selenium beautifulsoup4 lxml
```

Or with virtual environment:
```bash
python3 -m venv aws_env
source aws_env/bin/activate
pip3 install selenium beautifulsoup4 lxml
```

### Step 2: Verify Container is Running
```bash
podman ps | grep selenium
```

Should show the container running on port 4444.

### Step 3: Run the Scraper
```bash
python3 aws_scraper_remote.py
```

The script will:
1. Connect to Selenium container
2. Navigate to AWS event catalog
3. Scroll through page (load all events)
4. Parse event information  
5. Export to JSON & CSV
6. Print summary

**Typical runtime: 3-4 minutes**

### Step 4: Check Output
```bash
# Verify files were created
ls -lh aws_events.*

# View a sample event
head -20 aws_events.csv
```

---

## 🎨 Use Your Data

### Option A: Excel/Google Sheets
Simply open `aws_events.csv` in Excel or Google Sheets

### Option B: Filter in Terminal
```bash
# Get all 300+ level events
grep "300\|400" aws_events.csv

# Get only Breakout sessions
grep "Breakout" aws_events.csv

# Get events at specific venue
grep "Caesars" aws_events.csv
```

### Option C: Python Analysis
```python
import json
events = json.load(open('aws_events.json'))

# Count events by level
levels = {}
for event in events:
    level = event.get('level', 'Unknown')
    levels[level] = levels.get(level, 0) + 1

print(levels)
# Output: {'200': 500, '300': 400, '100': 300, '400': 382}
```

### Option D: Database Import
```bash
# SQLite
sqlite3 events.db
sqlite> .mode csv
sqlite> .import aws_events.csv events

# PostgreSQL
psql -d mydb -c "CREATE TABLE events (title TEXT, code TEXT, ...)"
```

---

## 🔧 Common Commands

### Run Scraper with Options
```bash
# Faster (fewer scrolls, 50 instead of 100)
python3 aws_scraper_remote.py --max-scrolls 50

# Slower/more polite (more scrolls, longer waits)
python3 aws_scraper_remote.py --max-scrolls 150 --scroll-pause 2

# Custom output files
python3 aws_scraper_remote.py \
  --json reinvent_events.json \
  --csv reinvent_events.csv

# JSON only (skip CSV)
python3 aws_scraper_remote.py --no-csv

# Show help
python3 aws_scraper_remote.py --help
```

### Container Management
```bash
# Check container is running
podman ps | grep selenium

# View container logs
podman logs selenium-chrome

# View logs in real-time
podman logs -f selenium-chrome

# Stop container (save resources)
podman stop selenium-chrome

# Start container again
podman start selenium-chrome

# Remove container
podman rm selenium-chrome

# Recreate container from scratch
podman run -d --name selenium-chrome selenium/standalone-chrome:latest
```

### Debugging
```bash
# Watch the browser live (VNC)
vncviewer localhost:5900
# Password: secret

# Check Python packages
pip3 list | grep -E "selenium|beautifulsoup"

# Test connection to container
python3 -c "from selenium import webdriver; \
            driver = webdriver.Remote('http://localhost:4444'); \
            print('✓ Connected')"
```

---

## ❓ Troubleshooting

### "Failed to connect to Selenium container"
```bash
# Check if container is running
podman ps | grep selenium

# If not, start it
podman run -d --name selenium-chrome selenium/standalone-chrome:latest

# Verify port is accessible
podman port selenium-chrome
```

### "ModuleNotFoundError: No module named 'selenium'"
```bash
# Install packages
pip3 install --break-system-packages selenium beautifulsoup4 lxml

# Or use virtual environment (better)
python3 -m venv myenv
source myenv/bin/activate
pip3 install selenium beautifulsoup4 lxml
python3 aws_scraper_remote.py
```

### "Timeout during page load"
The page might be slow. Try:
```bash
python3 aws_scraper_remote.py --scroll-pause 2.5
```

### "No events extracted"
Something might be wrong with parsing. Debug:
```bash
# Watch the browser (VNC)
vncviewer localhost:5900

# Or check the page manually
# https://registration.awsevents.com/flow/awsevents/reinvent2026/eventcatalog/page/eventcatalog?search=
```

---

## 📊 Output Preview

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
  {
    "title": "10 tips for querying Apache Iceberg data with Amazon Redshift",
    "code": "ANT319",
    "session_type": "Chalk talk",
    "level": "300",
    "time": "4:00 p.m. Monday, Nov 30",
    "location": "MGM Grand",
    "track": "",
    "description": ""
  }
]
```

### aws_events.csv
```csv
title,code,session_type,level,time,location,track,description
"10x or bust: How Amazon's Frontier teams ship with Kiro","DVT206","Breakout session","200","8:00 a.m. Monday, Nov 30","Caesars Forum","",""
"10 tips for querying Apache Iceberg data with Amazon Redshift","ANT319","Chalk talk","300","4:00 p.m. Monday, Nov 30","MGM Grand","",""
```

---

## 💡 Pro Tips

1. **Keep container running** — It stays up between runs, saves startup time
2. **Use `--scroll-pause 1.0`** — Faster on good connections
3. **Monitor with VNC** — See exactly what's happening: `vncviewer localhost:5900`
4. **Run multiple times** — Container handles it, just use different output files
5. **Filter before importing** — Use grep to filter CSV before Excel import
6. **Check container logs** — `podman logs selenium-chrome` helps debug

---

## 🎓 Educational Value

This scraper demonstrates:
- ✅ Selenium WebDriver automation
- ✅ Remote browser control (Grid protocol)
- ✅ Headless browser testing
- ✅ JavaScript-heavy page scraping
- ✅ HTML parsing (BeautifulSoup)
- ✅ Data export (JSON & CSV)
- ✅ Error handling & retry logic
- ✅ Containerized automation (production pattern)

**Learn production-ready techniques!**

---

## ✅ Pre-Flight Checklist

Before running, verify:

- [ ] Podman installed: `podman --version`
- [ ] Container running: `podman ps | grep selenium`
- [ ] Python 3.7+: `python3 --version`
- [ ] Packages installed: `pip3 list | grep selenium`
- [ ] Internet connection: `ping 8.8.8.8`
- [ ] Disk space: `df -h` (need ~50 MB)
- [ ] Port 4444 accessible: `telnet localhost 4444`

---

## 🚀 Ready to Go!

```bash
# Copy and paste this to get started:
pip3 install selenium beautifulsoup4 lxml && \
podman ps | grep selenium && \
python3 aws_scraper_remote.py
```

**Questions?** See:
- Quick help: [QUICK_START.md](QUICK_START.md)
- Setup help: [SETUP_INSTRUCTIONS.md](SETUP_INSTRUCTIONS.md)
- Full docs: [REMOTE_SELENIUM_GUIDE.md](REMOTE_SELENIUM_GUIDE.md)
- Visual guide: [ARCHITECTURE.txt](ARCHITECTURE.txt)

---

## 📞 Need Help?

1. **Check container:** `podman ps`
2. **View logs:** `podman logs -f selenium-chrome`
3. **Monitor browser:** `vncviewer localhost:5900`
4. **Read docs:** See files above
5. **Debug script:** Add `--verbose` flag or modify Python code

---

**You've got this!** 🎉

Next step: Run `python3 aws_scraper_remote.py`

---

**Last updated:** 2026-09-24
**Status:** Ready to use ✅
