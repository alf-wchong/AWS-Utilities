# Walkthrough: Filtering AWS Partner Central Opportunities with the AWS CLI and jq

**Purpose:** Document the method used to extract AWS Partner Central opportunities and filter them down to a targeted report, so the process can be repeated by anyone without the original author.

---

## Why export once and filter locally?

A fair question: if the AWS CLI can filter, why not ask AWS for exactly the report we want? The approach here is to make one broad export, keep it as a file, and slice it locally with [jq](https://jqlang.org/). The reasoning, with the facts checked against AWS documentation as of 2026-11-24:

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

<details><summary><strong>References</strong></summary>
  
  - AWS CLI `list-opportunities` reference: https://docs.aws.amazon.com/cli/latest/reference/partnercentral-selling/list-opportunities.html
  - `ListOpportunities` API reference: https://docs.aws.amazon.com/partner-central/latest/APIReference/API_ListOpportunities.html
  - Partner Central Selling API quotas: https://docs.aws.amazon.com/partner-central/latest/developer-guide/quotas.html

</details>

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
| Projected yearly (derived, HTML report only) | `Amount` x 12, for rows whose `Frequency` is `Monthly` |

---

## 2. Prerequisites

- AWS CLI v2 with the `partnercentral-selling` service available
- Credentials with permission to call `partnercentral-selling:ListOpportunities` for the AWS catalog
- [`jq`](https://jqlang.org/) installed (1.6 or later; the HTML step uses `--rawfile`, which needs 1.6)
- A web browser to open the HTML report
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

This step turns `report.json` into a single, self-contained, interactive HTML file with two tabs: a sortable, filterable list of opportunities, and a statistics tab that recalculates from whatever the filters show.

**Requires:**

- `report.json` from Step 2 (the filtered array). Without it, jq fails with "Could not open report.json", or, worse, silently builds a report from an old file.
- `EARLIEST_CLOSE_DATE` set in the current terminal session, to the same value used in Step 2. It is an ordinary shell variable, so no `export` is needed, but it disappears when you open a new terminal. If it is unset, the header shows a blank cutoff. You may type the literal date in place of `"$EARLIEST_CLOSE_DATE"` in the run command if you prefer.

### What the report contains

| Part | Content |
|------|---------|
| Header | Title, earliest close date used, data snapshot time, report generation time |
| Tab 1, Opportunities | Table of all opportunities with the seven report fields plus Projected yearly. Every column sorts, and every column has a filter control suited to its datatype. A count line shows "Showing X of Y", and a "Clear filters" button resets everything |
| Tab 2, Statistics | Summary cards, a by-frequency table and a by-close-month table, all computed from the rows that pass the filters on Tab 1 |

Properties of the output file:

- One file, no external resources (no CDN, fonts or libraries), so it opens offline.
- The data is embedded in the page as JSON, and the browser draws the tables with JavaScript. JavaScript must be enabled.
- Light and dark mode follow the viewer's system setting.
- All data is inserted as plain text, so a company name such as `A & B <Corp>` or one containing `</script>` cannot break the page.

### 5a. The page template

Copy the [report.template.html](./report.template.html) file into the same folder as `report.json`. It holds the page layout and all the JavaScript. The text `__REPORT_DATA__` is a placeholder that jq replaces with the data, so it must appear exactly once and must not be edited.

### 5b. The jq program

Copy the [report-tabs.jq](./report-tabs.jq) file into the same folder as `report.json`. It embeds the data into the template, and it needs no editing between runs because every changing value is passed in as an argument. 

How it works:

| Piece | Explanation |
|-------|-------------|
| `{ meta: {...}, rows: . }` | Wraps the filtered array and the three header values into one object |
| `tojson` | Serializes that object as JSON text |
| `split("<") \| join("\\u003c")` | Replaces every `<` with the JSON escape `\u003c`, so data can never close the `<script>` tag early. The browser reads it back as `<` |
| `$tpl` | The template text, passed in with `--rawfile` |
| `split("__REPORT_DATA__") \| join($json)` | Swaps the placeholder for the embedded data |

### 5c. Run it

```bash
# EARLIEST_CLOSE_DATE must already be set, to the same value used in Step 2
SNAPSHOT_TIME=$(command date -u -r PQT-Opptys.json '+%Y-%m-%d %H:%M UTC')
GENERATED_TIME=$(command date -u '+%Y-%m-%d %H:%M UTC')
echo "$EARLIEST_CLOSE_DATE | $SNAPSHOT_TIME | $GENERATED_TIME"   # all three must be non-empty

jq -r \
  --rawfile tpl report.template.html \
  --arg earliest_close "$EARLIEST_CLOSE_DATE" \
  --arg snapshot  "$SNAPSHOT_TIME" \
  --arg generated "$GENERATED_TIME" \
  -f report-tabs.jq report.json > report.html
```

Then open `report.html` in a browser (for example `explorer.exe report.html` from WSL, or `xdg-open report.html` on Linux).

| Argument | Purpose |
|----------|---------|
| `-r` | Raw output, so the HTML is written as text rather than as a quoted JSON string |
| `--rawfile tpl` | Reads `report.template.html` into the jq variable `$tpl` |
| `--arg earliest_close` | Shown in the header, so the report states the cutoff it was built with |
| `--arg snapshot` | Data snapshot time (the dump file's modification time, see Caveat 8) |
| `--arg generated` | Time the report was built |
| `-f report-tabs.jq` | Reads the program from the file |
| `report.json` | Input: the filtered array from Step 2 |

The `echo` line is a safety check. If any of the three values is blank, fix that before trusting the header. `command date` is used instead of plain `date` to bypass any shell alias (see Caveat 11).

### 5d. Using the report

Filter controls on Tab 1:

| Column | Type | Control |
|--------|------|---------|
| Id, Partner Opportunity Id, Customer | Text | "Contains" box, case-insensitive |
| Target close | Date | From and to date pickers |
| Stage, Frequency | Category | Dropdown of the values present in the data |
| Amount, Projected yearly | Number | Minimum and maximum boxes |

- Click a column header to sort ascending, and click again for descending. Id sorts naturally (O2 before O10).
- Filters combine: a row must pass every active filter.
- Switching to the Statistics tab shows figures for the filtered rows only. A note at its top states how many of the total rows are covered.
- Amount filters mix frequencies, because Amount is in whatever unit its row's `Frequency` says. Combine with the Frequency dropdown for a meaningful range.

### 5e. Statistics definitions

| Statistic | Definition |
|-----------|------------|
| Opportunities | Number of rows passing the filters |
| Distinct customers | Number of unique `CompanyName` values among them |
| Earliest / latest close | Minimum and maximum `TargetCloseDate` |
| Total expected spend (per frequency) | Sum of `Amount` for rows sharing a `Frequency`. Frequencies are never added together |
| Projected yearly (Monthly rows) | Sum of Projected yearly over the `Monthly` rows. See Caveat 10 |
| By frequency table | Per-frequency count, total, average, lowest and highest `Amount`, and projected yearly total (Monthly only) |
| By target close month | Count of opportunities per `YYYY-MM` |

To change the page (columns, statistics, styling), edit `report.template.html` and re-run 5c. The jq program does not change.

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
6. **Totals are per frequency and assume a single currency.** The report fields do not include `CurrencyCode`, so the report's totals and projected yearly figures cannot tell currencies apart. Check the dump before trusting them:
   ```bash
   jq '[.OpportunitySummaries[].Project.ExpectedCustomerSpend[0].CurrencyCode] | unique' PQT-Opptys.json
   ```
   A single value (for example `["USD"]`) means the totals are safe.
7. **Unreadable amounts count as 0.** If `Amount` is missing or not numeric, the HTML report treats it as 0 (and its projected yearly as 0.00) instead of failing, which lowers totals and averages without any warning.
8. **Snapshot time is the file's modification time.** Copying or re-saving `PQT-Opptys.json` changes it. Keep the original file, or set `SNAPSHOT_TIME` by hand to the true export time before running 5c.
9. **The HTML contains customer data and scripts.** It carries the same customer names and spend as the dump, so handle and share it under the same rules. Some corporate mail gateways block or quarantine HTML attachments that contain scripts, so send a test copy before relying on email.
10. **Projected yearly is a flat annualization.** It is `Amount` x 12, calculated only for rows whose `Frequency` is exactly `Monthly`. Other rows show an en dash and are left out of the yearly total. It is a run-rate figure, not a forecast: ramp-up, seasonality and a partial first year are not modeled. To see which frequencies occur in the dump:
    ```bash
    jq '[.OpportunitySummaries[].Project.ExpectedCustomerSpend[].Frequency] | group_by(.) | map({frequency: .[0], count: length})' PQT-Opptys.json
    ```
11. **A `date` alias breaks the timestamps.** An alias such as `alias date='date "+%a %b %d %H:%M:%S %Z %Y"'` in `~/.bashrc` makes `$(date ...)` fail with "extra operand". jq still runs, but the report header shows blank times. Fix it with `unalias date` (and remove the alias from `~/.bashrc`), or use `command date`, as 5c does.

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

# 4. Build the HTML report (Section 5). Save report.template.html (5a) and
#    report-tabs.jq (5b) once, then:
SNAPSHOT_TIME=$(command date -u -r PQT-Opptys.json '+%Y-%m-%d %H:%M UTC')
GENERATED_TIME=$(command date -u '+%Y-%m-%d %H:%M UTC')
echo "$EARLIEST_CLOSE_DATE | $SNAPSHOT_TIME | $GENERATED_TIME"   # all three non-empty
jq -r \
  --rawfile tpl report.template.html \
  --arg earliest_close "$EARLIEST_CLOSE_DATE" \
  --arg snapshot  "$SNAPSHOT_TIME" \
  --arg generated "$GENERATED_TIME" \
  -f report-tabs.jq report.json > report.html
```

---
