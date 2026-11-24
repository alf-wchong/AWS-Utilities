# Walkthrough: Filtering AWS Partner Central Opportunities with the AWS CLI and jq

**Purpose:** Document the method used to extract AWS Partner Central opportunities and filter them down to a targeted report, so the process can be repeated by anyone without the original author.

---

## Why export once and filter locally?

A fair question: if the AWS CLI can filter, why not ask AWS for exactly the report we want? The approach here is to make one broad export, keep it as a file, and slice it locally with jq. 

### What `list-opportunities` can and cannot filter server-side

| Our criterion | Server-side filter available? |
|---------------|-------------------------------|
| Earliest close date | Yes. `TargetCloseDate` accepts `AfterTargetCloseDate` / `BeforeTargetCloseDate` in `YYYY-MM-DD` format. The documentation says "after this date" and does not say whether the boundary date itself is included |
| Stage | Yes. `--life-cycle-stage` |
| Review status | Yes. `--life-cycle-review-status` |
| Spend `TargetCompany` | **No.** `ListOpportunities` has no filter on spend entries |

Other available filters: last modified date, customer company name, and opportunity identifier. The API can also sort by `TargetCloseDate`, `CreatedDate`, `LastModifiedDate`, `Identifier` or `CustomerCompanyName`.

So three of our four criteria *could* be pushed to the server. The reasons for doing it locally anyway:

1. **A local step is needed regardless.** The `TargetCompany == "AWS"` criterion cannot be filtered server-side, so jq must run no matter what.
2. **One dump answers many questions.** Changing the cutoff date, adding a stage, or asking a new question about the same data costs nothing locally. Server-side filtering means a new API call for every variation.
3. **Reproducibility and audit.** A dated export is a fixed snapshot. The report can be regenerated, compared to a later snapshot, or explained after the fact. A live query returns moving data, so two runs on different days can differ and the first answer is gone.
4. **Explicit boundary semantics.** With jq, `>=` is exactly what is written. The server-side date filter's inclusive/exclusive behavior is undocumented, so relying on it risks an off-by-one-day error at the boundary.
5. **Fewer calls to the SaaS.** `ListOpportunities` is a read action with per-partner-account quotas of 10 requests per second and 100,000 per 24 hours, and the service answers with a `ThrottlingException` when a limit is hit. These limits are generous for an occasional export, so this is a hygiene point rather than the main driver. It matters more if the process is ever automated or run often. AWS's own guidance for keeping a system in sync is to list only newly created or updated opportunities, and it recommends relying on Amazon EventBridge events rather than repeated polling.

The export command already applies one coarse server-side filter (`--life-cycle-stage`) to keep the dump to a relevant superset. The fine-grained criteria are applied locally.

### Trade-offs

- **Staleness.** The dump is a snapshot. Re-export before relying on it for a decision.
- **Data handling.** The file contains customer names and expected spend. Store and share it according to your organization's data-handling rules, and delete old dumps when they are no longer needed.

### General practice

Extracting once from a source system and transforming locally is a common data-engineering pattern. It is a convention and an engineering judgment, not a rule, and the reasons above are what justify it here.

### References

- AWS CLI `list-opportunities` reference: https://docs.aws.amazon.com/cli/latest/reference/partnercentral-selling/list-opportunities.html
- `ListOpportunities` API reference: https://docs.aws.amazon.com/partner-central/latest/APIReference/API_ListOpportunities.html
- Partner Central Selling API quotas: https://docs.aws.amazon.com/partner-central/latest/developer-guide/quotas.html

---

## 1. Goal

Produce a report of AWS Partner Central opportunities that meet **all** of these criteria:

| # | Criterion | Field |
|---|-----------|-------|
| 1 | Target close date is on or after `EARLIEST_CLOSE_DATE` (a variable, see below) | `LifeCycle.TargetCloseDate` |
| 2 | Stage is `Technical Validation` | `LifeCycle.Stage` |
| 3 | Expected customer spend targets AWS | `Project.ExpectedCustomerSpend[].TargetCompany` |
| 4 | Review status is `Approved` | `LifeCycle.ReviewStatus` |

**Variable: `EARLIEST_CLOSE_DATE`**

- Format: `YYYY-MM-DD` (ISO date).
- To express "month M of year Y or later", use the first day of that month. Example: "May 2027 or later" is `2027-05-01`.
- The comparison is inclusive (`>=`), so opportunities closing exactly on `EARLIEST_CLOSE_DATE` are included.
- Reference run: `EARLIEST_CLOSE_DATE=2027-05-01`.

Each matching opportunity is reported with these attributes:

| Report field | Source field |
|--------------|--------------|
| Id | `Id` |
| PartnerOpportunityIdentifier | `PartnerOpportunityIdentifier` |
| TargetCloseDate | `LifeCycle.TargetCloseDate` |
| Stage | `LifeCycle.Stage` |
| CompanyName | `Customer.Account.CompanyName` |
| Amount | `Project.ExpectedCustomerSpend[].Amount` |
| Frequency | `Project.ExpectedCustomerSpend[].Frequency` |

---

## 2. Prerequisites

- AWS CLI v2 with the `partnercentral-selling` service available
- Credentials with permission to call `partnercentral-selling:ListOpportunities` for the AWS catalog
- `jq` installed (works with 1.6 and later)
- Region: `us-east-1`

---

## 3. Step 1: Export the opportunities

```bash
aws partnercentral-selling list-opportunities \
  --catalog AWS \
  --lifecycle-stage "Prospect" "Qualified" "Technical Validation" \
  --region us-east-1 > PQT-Opptys.json
```

Notes:

- The command pulls three lifecycle stages. The final report only needs `Technical Validation`, but the broader export lets the same dump serve other questions.
- The AWS CLI paginates automatically for this operation, so the result is a single JSON object rather than multiple pages.
- The output is a single JSON object with the opportunities under the `OpportunitySummaries` key, not a bare array. All jq commands below iterate with `.OpportunitySummaries[]`. Using `.[]` at the top level fails with `jq: error: Cannot index array with string "Project"`.
- Quick sanity check on the export: `jq '.OpportunitySummaries | length' PQT-Opptys.json`

---

## 4. Step 2: Filter and report

Set the variable, then run the filter. The variable is passed into jq with `--arg`, so the jq program itself never needs editing.

```bash
EARLIEST_CLOSE_DATE="2027-05-01"

jq --arg earliest_close "$EARLIEST_CLOSE_DATE" '[
  .OpportunitySummaries[]
  | select(
      .LifeCycle.TargetCloseDate >= $earliest_close
      and .LifeCycle.Stage == "Technical Validation"
      and .LifeCycle.ReviewStatus == "Approved"
      and .Project.ExpectedCustomerSpend[0].TargetCompany == "AWS"
    )
  | {
      Id,
      PartnerOpportunityIdentifier,
      TargetCloseDate: .LifeCycle.TargetCloseDate,
      Stage: .LifeCycle.Stage,
      CompanyName: .Customer.Account.CompanyName,
      Amount: .Project.ExpectedCustomerSpend[0].Amount,
      Frequency: .Project.ExpectedCustomerSpend[0].Frequency
    }
]' PQT-Opptys.json > report.json
```

### How it works

| Piece | Explanation |
|-------|-------------|
| `--arg earliest_close "$EARLIEST_CLOSE_DATE"` | Passes the shell variable into jq as the string `$earliest_close` |
| `.OpportunitySummaries[]` | Iterates every opportunity in the wrapper array |
| `select(... and ...)` | Keeps only objects that satisfy all four conditions |
| `TargetCloseDate >= $earliest_close` | `TargetCloseDate` is an ISO `YYYY-MM-DD` string, so lexical comparison equals chronological comparison |
| `ExpectedCustomerSpend[0].TargetCompany` | Uses the first (and, in the reference snapshot, only) spend entry. See Caveat 1 |
| `{ Id, ... }` | Builds the output object with only the requested fields. Nested fields are renamed for flat, readable output |
| Outer `[ ... ]` | Collects the results back into a JSON array |

### Count the matches

```bash
jq --arg earliest_close "$EARLIEST_CLOSE_DATE" '[
  .OpportunitySummaries[]
  | select(
      .LifeCycle.TargetCloseDate >= $earliest_close
      and .LifeCycle.Stage == "Technical Validation"
      and .LifeCycle.ReviewStatus == "Approved"
      and .Project.ExpectedCustomerSpend[0].TargetCompany == "AWS"
    )
] | length' PQT-Opptys.json
```

Counting in general:

```bash
jq '.OpportunitySummaries | length' PQT-Opptys.json   # all opportunities
jq 'length' file.json                                  # for a bare top-level array
```

---

## 5. Step 3: Generate the HTML report

This step turns `report.json` (the output of Step 2) into a single, self-contained HTML file with statistics at the top and the bottom. The transformation is done with jq as well, so no new tools are required.

### What the report contains

| Part | Content |
|------|---------|
| Header | Title, earliest close date used, data snapshot time, report generation time |
| Top statistics | Summary cards: number of opportunities, distinct customers, earliest and latest target close date, and total expected spend per `Frequency` |
| Detail table | One row per opportunity, sorted by target close date then company name, with the seven report fields |
| Bottom statistics | The same summary cards repeated, plus a breakdown by `Frequency` (count, total, average, lowest, highest) and a count of opportunities per close month |

Properties of the output file:

- One file, no external resources (no CDN, fonts or scripts), so it opens offline and can be attached to an email.
- Light and dark mode follow the viewer's system setting.
- All text from the data is HTML-escaped, so a company name such as `A & B <Corp>` cannot break the page.
- `Amount` is shown as a number with thousands separators and two decimals.

### 5a. Save the jq program

Save this once as `report.jq`. It does not need editing between runs, because every changing value is passed in as an argument.

```bash
cat > report.jq << 'EOF'
def esc: tostring | @html;
def group3: if length <= 3 then . else (.[:-3] | group3) + "," + .[-3:] end;
def money:
  (. * 100 | round) as $c
  | ($c / 100 | floor) as $d
  | ($c % 100) as $r
  | ($d | tostring | group3) + "." + (if $r < 10 then "0" else "" end) + ($r | tostring);
def card($label; $value):
  "<div class='card'><div class='label'>\($label | esc)</div><div class='value'>\($value | esc)</div></div>";

map(. + {AmountNum: ((.Amount | tonumber?) // 0)}) as $rows
| ($rows | length) as $n
| ($rows | map(.CompanyName) | unique | length) as $customers
| ($rows | group_by(.Frequency) | map({
    freq: (.[0].Frequency // "Unspecified"),
    count: length,
    total: (map(.AmountNum) | add),
    avg: ((map(.AmountNum) | add) / length),
    lowest: (map(.AmountNum) | min),
    highest: (map(.AmountNum) | max)
  })) as $byfreq
| ($rows | group_by((.TargetCloseDate // "n/a")[0:7]) | map({
    month: ((.[0].TargetCloseDate // "n/a")[0:7]),
    count: length
  })) as $bymonth
| (
    card("Opportunities"; $n)
    + card("Distinct customers"; $customers)
    + card("Earliest close"; ($rows | map(.TargetCloseDate) | min // "n/a"))
    + card("Latest close"; ($rows | map(.TargetCloseDate) | max // "n/a"))
    + ($byfreq | map(card("Total expected spend (\(.freq))"; (.total | money))) | join(""))
  ) as $summary
| (
    if $n == 0 then
      "<tr><td colspan='7'>No opportunities matched the criteria.</td></tr>"
    else
      $rows | sort_by(.TargetCloseDate, .CompanyName) | map(
        "<tr><td>\(.Id | esc)</td><td>\(.PartnerOpportunityIdentifier | esc)</td><td>\(.TargetCloseDate | esc)</td><td>\(.Stage | esc)</td><td>\(.CompanyName | esc)</td><td class='num'>\(.AmountNum | money)</td><td>\(.Frequency | esc)</td></tr>"
      ) | join("\n")
    end
  ) as $tbody
| ($byfreq | map(
    "<tr><td>\(.freq | esc)</td><td class='num'>\(.count)</td><td class='num'>\(.total | money)</td><td class='num'>\(.avg | money)</td><td class='num'>\(.lowest | money)</td><td class='num'>\(.highest | money)</td></tr>"
  ) | join("\n")) as $freqrows
| ($bymonth | map(
    "<tr><td>\(.month | esc)</td><td class='num'>\(.count)</td></tr>"
  ) | join("\n")) as $monthrows
| "<!DOCTYPE html>
<html lang='en'>
<head>
<meta charset='utf-8'>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>Technical Validation opportunities</title>
<style>
:root { color-scheme: light dark; --bg:#ffffff; --fg:#1f2328; --muted:#59636e; --card:#f6f8fa; --line:#d1d9e0; --accent:#0969da; }
@media (prefers-color-scheme: dark) {
  :root { --bg:#0d1117; --fg:#e6edf3; --muted:#9198a1; --card:#161b22; --line:#30363d; --accent:#58a6ff; }
}
body { background:var(--bg); color:var(--fg); font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif; margin:0; padding:24px; line-height:1.45; }
h1 { margin:0 0 4px; font-size:1.5rem; }
h2 { margin:32px 0 12px; font-size:1.2rem; border-bottom:1px solid var(--line); padding-bottom:6px; }
h3 { margin:20px 0 8px; font-size:1rem; color:var(--muted); }
.meta { color:var(--muted); font-size:0.9rem; margin:0 0 16px; }
.stats { display:flex; flex-wrap:wrap; gap:12px; margin:16px 0; }
.card { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:12px 16px; min-width:150px; }
.label { color:var(--muted); font-size:0.8rem; }
.value { color:var(--accent); font-size:1.4rem; font-weight:600; }
.wrap { overflow-x:auto; }
table { border-collapse:collapse; width:100%; font-size:0.9rem; }
th, td { text-align:left; padding:6px 10px; border-bottom:1px solid var(--line); white-space:nowrap; }
th { background:var(--card); position:sticky; top:0; }
td.num, th.num { text-align:right; font-variant-numeric:tabular-nums; }
table.small { width:auto; min-width:360px; }
</style>
</head>
<body>
<h1>Technical Validation opportunities</h1>
<p class='meta'>Approved, targeting AWS, closing on or after \($earliest_close | esc) &middot; Data snapshot: \($snapshot | esc) &middot; Report generated: \($generated | esc)</p>

<section class='stats'>\($summary)</section>

<div class='wrap'>
<table>
<thead><tr><th>Id</th><th>Partner Opportunity Id</th><th>Target close</th><th>Stage</th><th>Customer</th><th class='num'>Amount</th><th>Frequency</th></tr></thead>
<tbody>
\($tbody)
</tbody>
</table>
</div>

<h2>Statistics</h2>
<section class='stats'>\($summary)</section>

<h3>By frequency</h3>
<div class='wrap'>
<table class='small'>
<thead><tr><th>Frequency</th><th class='num'>Count</th><th class='num'>Total</th><th class='num'>Average</th><th class='num'>Lowest</th><th class='num'>Highest</th></tr></thead>
<tbody>
\($freqrows)
</tbody>
</table>
</div>

<h3>By target close month</h3>
<div class='wrap'>
<table class='small'>
<thead><tr><th>Month</th><th class='num'>Opportunities</th></tr></thead>
<tbody>
\($monthrows)
</tbody>
</table>
</div>
</body>
</html>
"
EOF
```

### 5b. Run it

```bash
jq -r \
  --arg earliest_close "$EARLIEST_CLOSE_DATE" \
  --arg snapshot  "$(date -u -r PQT-Opptys.json +'%Y-%m-%d %H:%M UTC')" \
  --arg generated "$(date -u +'%Y-%m-%d %H:%M UTC')" \
  -f report.jq report.json > report.html
```

Then open `report.html` in a browser (for example `explorer.exe report.html` from WSL, or `xdg-open report.html` on Linux).

| Argument | Purpose |
|----------|---------|
| `-r` | Raw output, so the HTML is written as text rather than as a quoted JSON string |
| `--arg earliest_close` | Shown in the header, so the report states the cutoff it was built with |
| `--arg snapshot` | Modification time of the dump, used as the data snapshot time |
| `--arg generated` | Time the report was built |
| `-f report.jq` | Reads the program from the file |
| `report.json` | Input: the filtered array from Step 2 |

### 5c. Statistics definitions

| Statistic | Definition |
|-----------|------------|
| Opportunities | Number of rows in `report.json` |
| Distinct customers | Number of unique `CompanyName` values |
| Earliest / latest close | Minimum and maximum `TargetCloseDate` |
| Total expected spend (per frequency) | Sum of `Amount` for rows sharing a `Frequency`. Frequencies are never added together |
| Average, lowest, highest | Per-frequency mean, minimum and maximum of `Amount` |
| By target close month | Count of opportunities per `YYYY-MM` |

To change which statistics appear at the top or bottom, edit `$summary` (the cards) or the "By frequency" and "By target close month" sections of the HTML template. `$summary` is reused in both places, so a change to it shows up at the top and the bottom.

---

## 6. Known caveats

1. **One spend entry per opportunity is assumed.** `ExpectedCustomerSpend` is an array, and the filter reads index `[0]`. In the 2026-11-24 snapshot, all 832 opportunities had exactly one entry, so this is safe. If AWS ever returns opportunities with multiple entries, `[0]` will silently ignore the rest. A quick re-check, if ever needed:
   ```bash
   jq 'any(.OpportunitySummaries[]; (.Project.ExpectedCustomerSpend // []) | length > 1)' PQT-Opptys.json
   ```
   `false` means the assumption still holds.
2. **Missing dates are silently excluded.** If `TargetCloseDate` is missing or `null`, jq treats it as less than any string, so the record fails the `>=` test and drops out without an error.
3. **`Amount` is a string** (for example `"862.0"`). To get a number, use `(.Project.ExpectedCustomerSpend[0].Amount | tonumber)`.
4. **Amounts are not normalized.** `Frequency` can differ between rows (for example Monthly vs. other values), so do not total the `Amount` column without converting to a common period.
5. **Date format matters.** `EARLIEST_CLOSE_DATE` must be `YYYY-MM-DD`. A value like `05/2027` or `2027-5-1` will compare incorrectly because the comparison is a plain string comparison.
6. **Totals are per frequency and assume a single currency.** The report fields do not include `CurrencyCode`, so the HTML totals cannot tell currencies apart. Check the dump before trusting the totals:
   ```bash
   jq '[.OpportunitySummaries[].Project.ExpectedCustomerSpend[0].CurrencyCode] | unique' PQT-Opptys.json
   ```
   A single value (for example `["USD"]`) means the totals are safe.
7. **Unreadable amounts count as 0.** If `Amount` is missing or not numeric, the HTML report treats it as 0 instead of failing, which lowers totals and averages without any warning.
8. **Snapshot time is the file's modification time.** Copying or re-saving `PQT-Opptys.json` changes it. Keep the original file, or pass the true export time as the `snapshot` argument.
9. **The HTML contains customer data.** It carries the same customer names and spend as the dump. Handle and share it under the same rules.

---

## 7. Optional: CSV output

For a spreadsheet-friendly version, emit rows instead of a JSON array:

```bash
jq -r --arg earliest_close "$EARLIEST_CLOSE_DATE" '
  .OpportunitySummaries[]
  | select(
      .LifeCycle.TargetCloseDate >= $earliest_close
      and .LifeCycle.Stage == "Technical Validation"
      and .LifeCycle.ReviewStatus == "Approved"
      and .Project.ExpectedCustomerSpend[0].TargetCompany == "AWS"
    )
  | [
      .Id,
      .PartnerOpportunityIdentifier,
      .LifeCycle.TargetCloseDate,
      .LifeCycle.Stage,
      .Customer.Account.CompanyName,
      .Project.ExpectedCustomerSpend[0].Amount,
      .Project.ExpectedCustomerSpend[0].Frequency
    ]
  | @csv
' PQT-Opptys.json > report.csv
```

To add a header row, write it first and append the jq output:

```bash
echo 'Id,PartnerOpportunityIdentifier,TargetCloseDate,Stage,CompanyName,Amount,Frequency' > report.csv
jq -r --arg earliest_close "$EARLIEST_CLOSE_DATE" '...same filter as above...' PQT-Opptys.json >> report.csv
```

---

## 8. Quick-start summary

```bash
# 0. Set the cutoff (YYYY-MM-DD)
EARLIEST_CLOSE_DATE="2027-05-01"

# 1. Export
aws partnercentral-selling list-opportunities \
  --catalog AWS \
  --lifecycle-stage "Prospect" "Qualified" "Technical Validation" \
  --region us-east-1 > PQT-Opptys.json

# 2. Sanity check size
jq '.OpportunitySummaries | length' PQT-Opptys.json

# 3. Run the filter (Section 4) and write report.json / report.csv

# 4. Build the HTML report (Section 5): save report.jq once, then
jq -r \
  --arg earliest_close "$EARLIEST_CLOSE_DATE" \
  --arg snapshot  "$(date -u -r PQT-Opptys.json +'%Y-%m-%d %H:%M UTC')" \
  --arg generated "$(date -u +'%Y-%m-%d %H:%M UTC')" \
  -f report.jq report.json > report.html
```

---
