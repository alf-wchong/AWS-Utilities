# Quick Start - AWS re:Invent Scraper (5 minutes)

## 1️⃣ Install Python packages
```bash
pip3 install selenium beautifulsoup4 lxml
```

## 2️⃣ Verify Selenium container is running
```bash
podman ps | grep selenium
```

**If not running, start it:**
```bash
podman run -d --name selenium-chrome selenium/standalone-chrome:latest
```

## 3️⃣ Run the scraper
```bash
python3 aws_scraper_remote.py
```

**That's it!** ✅

---

## Output Files

```
aws_events.json   ← Machine-readable, all event data
aws_events.csv    ← Spreadsheet-friendly (Excel/Sheets)
```

---

## Common Commands

```bash
# Basic run
python3 aws_scraper_remote.py

# Faster (fewer scrolls)
python3 aws_scraper_remote.py --max-scrolls 50

# Slower (more polite)
python3 aws_scraper_remote.py --max-scrolls 150 --scroll-pause 2

# Custom filenames
python3 aws_scraper_remote.py --json my_events.json --csv my_events.csv

# JSON only (no CSV)
python3 aws_scraper_remote.py --no-csv

# CSV only (no JSON)
python3 aws_scraper_remote.py --no-json

# All options
python3 aws_scraper_remote.py \
  --host localhost \
  --port 4444 \
  --max-scrolls 100 \
  --scroll-pause 1.5 \
  --json events.json \
  --csv events.csv
```

---

## Typical Runtime

| Action | Time |
|--------|------|
| Page load | 3-5 sec |
| Scrolling (load all events) | 2-3 min |
| Parsing | 30 sec |
| **Total** | **~3-4 min** |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Failed to connect to Selenium" | Check: `podman ps \| grep selenium` |
| Container not running | Start: `podman run -d --name selenium-chrome selenium/standalone-chrome:latest` |
| Timeout during scroll | Increase max-scrolls: `--max-scrolls 150` |
| Want to see what's happening | Connect VNC to `localhost:5900` |
| Output files not created | Check script permissions: `chmod +x aws_scraper_remote.py` |

---

## File Formats

### JSON (aws_events.json)
Use for: APIs, automation, data processing
```json
[
  {
    "title": "Session Title",
    "code": "DVT206",
    "session_type": "Breakout session",
    "level": "200",
    "time": "8:00 a.m. Monday, Nov 30",
    "location": "Caesars Forum"
  }
]
```

### CSV (aws_events.csv)
Use for: Excel, Google Sheets, data analysis
```
title,code,session_type,level,time,location
"Session Title","DVT206","Breakout session","200","8:00 a.m. Monday, Nov 30","Caesars Forum"
```

---

## One-Line Setup & Run

```bash
pip3 install selenium beautifulsoup4 lxml && python3 aws_scraper_remote.py
```

---

## Need Help?

Full documentation: `REMOTE_SELENIUM_GUIDE.md`

Quick questions:
- **Can I run multiple times?** Yes, container stays running
- **Can I change output location?** Yes: `--json /path/to/file.json`
- **Will it scrape event descriptions?** Partially (depends on page structure)
- **Can I filter events after?** Yes, both JSON and CSV can be filtered

---

## Stop When Done

```bash
# Stop the container (can restart later)
podman stop selenium-chrome

# Remove the container
podman rm selenium-chrome

# Remove and re-pull fresh image (if issues)
podman rmi selenium/standalone-chrome:latest
```

---

**You're ready!** 🎉 Just run: `python3 aws_scraper_remote.py`
