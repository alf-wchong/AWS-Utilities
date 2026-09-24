#!/usr/bin/env python3
"""
AWS re:Invent 2026 Event Scraper - Remote Selenium Container Version
Connects to a running podman/docker Selenium container
"""

import json
import csv
import sys
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from bs4 import BeautifulSoup


class RemoteAWSEventScraper:
    """Scraper using remote Selenium container"""
    
    def __init__(self, selenium_host="localhost", selenium_port=4444):
        """
        Initialize connection to remote Selenium container
        
        Args:
            selenium_host: Hostname/IP of Selenium container
            selenium_port: Port (default 4444 for Selenium Grid)
        """
        self.selenium_url = f"http://{selenium_host}:{selenium_port}"
        self.events = []
        self.driver = None
        
        print(f"🔗 Connecting to Selenium at {self.selenium_url}...")
        
        try:
            # Connect to remote Chrome via Selenium Grid protocol
            self.driver = webdriver.Remote(
                command_executor=f"{self.selenium_url}/wd/hub",
                desired_capabilities={"browserName": "chrome"}
            )
            print("✅ Connected to Selenium Chrome container")
        except Exception as e:
            print(f"❌ Failed to connect to Selenium container: {e}")
            print(f"\nMake sure the container is running:")
            print(f"  podman ps")
            print(f"\nIf not running, start it with:")
            print(f"  podman run -d --name selenium-chrome selenium/standalone-chrome:latest")
            print(f"\nOr if using different port:")
            print(f"  python3 aws_scraper_remote.py --host localhost --port 4444")
            sys.exit(1)
    
    def scrape_events(self, max_scrolls=100, scroll_pause=1.5, save_json=True, save_csv=True):
        """
        Main scraping workflow
        
        Args:
            max_scrolls: Maximum number of scrolls to load events
            scroll_pause: Seconds to wait between scrolls
            save_json: Whether to save JSON output
            save_csv: Whether to save CSV output
        """
        try:
            # Step 1: Navigate to page
            self._navigate()
            
            # Step 2: Load all events via scrolling
            self._scroll_load(max_scrolls, scroll_pause)
            
            # Step 3: Parse events
            self._parse_events()
            
            # Step 4: Save results
            if save_json:
                self.save_json()
            if save_csv:
                self.save_csv()
            
            # Step 5: Print summary
            self._print_summary()
            
            return self.events
        
        finally:
            self.close()
    
    def _navigate(self):
        """Navigate to AWS event catalog"""
        url = "https://registration.awsevents.com/flow/awsevents/reinvent2026/eventcatalog/page/eventcatalog?search="
        
        print(f"\n📂 Navigating to AWS event catalog...")
        self.driver.get(url)
        
        # Wait for page to load
        try:
            WebDriverWait(self.driver, 15).until(
                EC.presence_of_elements_located((By.TAG_NAME, "body"))
            )
            time.sleep(2)
            print("✅ Page loaded")
        except Exception as e:
            print(f"⚠️  Timeout during page load: {e}")
    
    def _scroll_load(self, max_scrolls=100, scroll_pause=1.5):
        """Scroll through page to load all events"""
        print(f"\n📜 Scrolling to load all events (max {max_scrolls} scrolls)...")
        
        last_height = self.driver.execute_script("return document.body.scrollHeight")
        scrolls = 0
        
        while scrolls < max_scrolls:
            # Scroll down
            self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
            time.sleep(scroll_pause)
            
            # Check if new content loaded
            new_height = self.driver.execute_script("return document.body.scrollHeight")
            
            if new_height == last_height:
                print(f"✅ Reached end of catalog after {scrolls} scrolls")
                break
            
            last_height = new_height
            scrolls += 1
            
            # Progress indicator
            sys.stdout.write(f"\r   Scrolled: {scrolls}/{max_scrolls}")
            sys.stdout.flush()
        
        print()  # Newline after progress
    
    def _parse_events(self):
        """Parse events from loaded page"""
        print("\n🔍 Parsing events...")
        
        # Get page source
        page_source = self.driver.page_source
        soup = BeautifulSoup(page_source, 'html.parser')
        
        # Extract events by looking for event blocks
        # The page structure has session info in divs and lists
        
        import re
        
        # Pattern 1: Find session codes like (DVT212-S) or (ANT319)
        text_content = soup.get_text()
        
        # Find all text that looks like event info
        # Format: "Title (CODE123)" followed by level, type, venue, time
        
        event_blocks = self._extract_from_html(soup)
        self.events = event_blocks
        
        print(f"✅ Extracted {len(self.events)} events")
    
    def _extract_from_html(self, soup):
        """Extract event details from BeautifulSoup object"""
        events = []
        
        # Strategy: Look for the hierarchical structure of the page
        # Typical: Breakout session > Level > Type > Venue > Time > Title > Description
        
        # Find all divs that contain event information
        page_text = soup.get_text()
        
        # Use regex to find session codes and associate with titles
        import re
        
        # Pattern for session codes: (DDD###-S?) where D=letters, #=numbers
        code_pattern = r'\(([A-Z]{3}\d{3}(?:-S)?)\)'
        title_pattern = r':\s*([A-Za-z0-9\s\'\(\)&:,\-\.]+?)\s*\('
        
        # Find all event references in the page
        lines = page_text.split('\n')
        current_event = {}
        
        for line in lines:
            line = line.strip()
            if not line:
                continue
            
            # Look for session type markers
            if any(stype in line for stype in ['Breakout session', 'Chalk talk', 'Workshop', 'Lightning talk', 'Code talk', 'Builders', 'Gamified learning']):
                if current_event.get('title'):
                    events.append(current_event)
                current_event = {'session_type': ''}
                for stype in ['Breakout session', 'Chalk talk', 'Workshop', 'Lightning talk', 'Code talk', 'Builders', 'Gamified learning']:
                    if stype in line:
                        current_event['session_type'] = stype
                        break
            
            # Look for level markers
            elif any(level in line for level in ['100 – ', '200 – ', '300 – ', '400 – ']):
                for level in ['100', '200', '300', '400']:
                    if level in line:
                        current_event['level'] = level
                        break
            
            # Look for venue names
            elif any(venue in line for venue in ['Caesars Palace', 'MGM Grand', 'Venetian', 'Wynn', 'Caesars Forum', 'Amazon Ballroom']):
                for venue in ['Caesars Palace', 'MGM Grand', 'Venetian', 'Wynn', 'Caesars Forum', 'Amazon Ballroom']:
                    if venue in line:
                        current_event['location'] = venue
                        break
            
            # Look for time patterns
            elif re.search(r'\d{1,2}:\d{2}\s*(?:a\.m\.|p\.m\.)', line):
                current_event['time'] = line
            
            # Look for session codes
            elif re.search(code_pattern, line):
                match = re.search(code_pattern, line)
                if match:
                    code = match.group(1)
                    # Extract title (text before the code)
                    before_code = line[:match.start()].strip()
                    if before_code and before_code not in ['', '–', '—']:
                        current_event['title'] = before_code
                        current_event['code'] = code
        
        # Add last event
        if current_event.get('title'):
            events.append(current_event)
        
        # Fill in defaults
        for event in events:
            event.setdefault('session_type', '')
            event.setdefault('level', '')
            event.setdefault('location', '')
            event.setdefault('time', '')
            event.setdefault('code', '')
            event.setdefault('description', '')
            event.setdefault('track', '')
        
        # Remove duplicates
        unique_events = {}
        for event in events:
            key = f"{event['title']}|{event['code']}"
            if key not in unique_events:
                unique_events[key] = event
        
        return list(unique_events.values())
    
    def save_json(self, filename="aws_events.json"):
        """Save events to JSON"""
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(self.events, f, indent=2, ensure_ascii=False)
        print(f"\n📄 Saved {len(self.events)} events to {filename}")
    
    def save_csv(self, filename="aws_events.csv"):
        """Save events to CSV"""
        if not self.events:
            print("No events to save")
            return
        
        keys = ['title', 'code', 'session_type', 'level', 'time', 'location', 'track', 'description']
        
        with open(filename, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=keys, extrasaction='ignore')
            writer.writeheader()
            for event in self.events:
                row = {k: event.get(k, '') for k in keys}
                writer.writerow(row)
        
        print(f"📊 Saved {len(self.events)} events to {filename}")
    
    def _print_summary(self):
        """Print summary of scraped events"""
        if not self.events:
            print("No events scraped")
            return
        
        print(f"\n{'='*70}")
        print(f"📊 AWS RE:INVENT 2026 SCRAPING SUMMARY")
        print(f"{'='*70}")
        print(f"Total events: {len(self.events)}")
        
        # Count by session type
        types = {}
        for event in self.events:
            stype = event.get('session_type', 'Unknown')
            types[stype] = types.get(stype, 0) + 1
        
        if types:
            print(f"\nBy Session Type:")
            for stype, count in sorted(types.items(), key=lambda x: x[1], reverse=True):
                if stype:
                    print(f"  {stype}: {count}")
        
        # Count by level
        levels = {}
        for event in self.events:
            level = event.get('level', 'Unknown')
            levels[level] = levels.get(level, 0) + 1
        
        if levels:
            print(f"\nBy Level:")
            for level in ['100', '200', '300', '400']:
                count = levels.get(level, 0)
                if count:
                    print(f"  Level {level}: {count}")
        
        # Count by location
        locations = {}
        for event in self.events:
            loc = event.get('location', 'Unknown')
            locations[loc] = locations.get(loc, 0) + 1
        
        if locations:
            print(f"\nBy Location:")
            for loc, count in sorted(locations.items(), key=lambda x: x[1], reverse=True):
                if loc:
                    print(f"  {loc}: {count}")
        
        print(f"\n{'='*70}")
        print("Sample events:")
        print(f"{'='*70}")
        for event in self.events[:3]:
            print(f"\n  📌 {event['title']}")
            if event.get('code'):
                print(f"     Code: {event['code']}")
            if event.get('session_type'):
                print(f"     Type: {event['session_type']} | Level: {event.get('level', 'N/A')}")
            if event.get('time'):
                print(f"     Time: {event['time']}")
            if event.get('location'):
                print(f"     Location: {event['location']}")
    
    def close(self):
        """Close the browser connection"""
        if self.driver:
            print("\n🔚 Closing browser connection...")
            self.driver.quit()
            print("✅ Browser closed")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description="AWS re:Invent 2026 Event Scraper (Remote Selenium Container)"
    )
    parser.add_argument(
        "--host",
        default="localhost",
        help="Selenium container host (default: localhost)"
    )
    parser.add_argument(
        "--port",
        type=int,
        default=4444,
        help="Selenium container port (default: 4444)"
    )
    parser.add_argument(
        "--max-scrolls",
        type=int,
        default=100,
        help="Maximum number of scrolls (default: 100)"
    )
    parser.add_argument(
        "--scroll-pause",
        type=float,
        default=1.5,
        help="Seconds to pause between scrolls (default: 1.5)"
    )
    parser.add_argument(
        "--json",
        default="aws_events.json",
        help="JSON output file (default: aws_events.json)"
    )
    parser.add_argument(
        "--csv",
        default="aws_events.csv",
        help="CSV output file (default: aws_events.csv)"
    )
    parser.add_argument(
        "--no-csv",
        action="store_true",
        help="Don't save CSV file"
    )
    parser.add_argument(
        "--no-json",
        action="store_true",
        help="Don't save JSON file"
    )
    
    args = parser.parse_args()
    
    print("🚀 AWS re:Invent 2026 Event Scraper (Remote Selenium)")
    print("="*70)
    
    scraper = RemoteAWSEventScraper(
        selenium_host=args.host,
        selenium_port=args.port
    )
    
    try:
        scraper.scrape_events(
            max_scrolls=args.max_scrolls,
            scroll_pause=args.scroll_pause,
            save_json=not args.no_json,
            save_csv=not args.no_csv
        )
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
        scraper.close()
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        scraper.close()


if __name__ == "__main__":
    main()
