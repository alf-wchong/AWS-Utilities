# Finding Intel Compute Spend in an AWS Account with CloudShell

A step-by-step walkthrough for estimating how much you spend on **Intel-based compute** (EC2, RDS, ElastiCache, OpenSearch, Redshift, and other services that report an instance type), using AWS CloudShell and the Cost Explorer API. No access keys, profiles, or AWS Organizations required.

---

## 1. Overview

AWS does not expose "Intel" as a Cost Explorer dimension. This approach:

1. Pulls monthly cost grouped by **Service** and **Instance Type** via `aws ce get-cost-and-usage`.
2. Classifies each instance family as **Intel**, **AMD**, **Graviton**, or **Other** from its name.
3. Writes summary CSVs you can download and combine across accounts.

It runs in **CloudShell**, which uses your console login credentials, so there is nothing to configure for authentication.

### What is covered

| Covered | Not covered |
|---|---|
| EC2 instances | EBS, snapshots, data transfer (no instance type) |
| RDS, ElastiCache, OpenSearch, Redshift, MemoryDB, MSK, EMR (EC2 portion) | Lambda, Fargate (billed by x86/Arm, not CPU vendor) |
| Any usage line that carries an instance type | EC2-Other lines with no instance type |

> **Important:** The total is a *lower-bound estimate of Intel compute spend*, based on instance-type naming. For exact per-CPU accuracy, use a CUR 2.0 Data Export and the `product_physical_processor` column (see [Section 9](#9-going-further-exact-accuracy)).

---

## 2. Prerequisites

- Access to the AWS Console for the account to be queried.
- Cost Explorer **enabled** in the account (it is enabled automatically the first time someone opens Cost Explorer in the console; data can take up to 24 hours to appear).
- IAM permission for **`ce:GetCostAndUsage`**. Administrators have this; many restricted users and roles do not.
- `sts:GetCallerIdentity` (allowed for all authenticated principals by default).

CloudShell already includes the AWS CLI v2, `jq`, and `python3`. Nothing needs installing.

---

## 3. How instance families are classified

The classifier looks at the instance family (for example `m7i`, `c6a`, `r7g`) and applies these rules, in order:

| Rule | Result | Examples |
|---|---|---|
| Family letters start with `trn`, `inf`, `dl` or `vt`, or are exactly `p`, `g` or `f` (accelerator and GPU/FPGA families) | Other | `trn1`, `inf2`, `p5`, `g5`, `f1` |
| Suffix contains `g` (Graviton) | Graviton | `m7g`, `c6gd`, `r6gn`, `t4g` |
| Suffix contains `a` | AMD | `m6a`, `c5a`, `t3a` |
| Anything else | Intel | `m5`, `m6i`, `c7i`, `r5n`, `t3`, `i3en`, `m7i-flex` |

Database and cache prefixes are handled automatically: `db.m6i.large`, `cache.r7g.large`, `ml.m5.xlarge`, and `m5.large.search` all resolve to their underlying family.

> **Caveats:** This is a name-based heuristic. A few families do not follow the pattern (for example some older or specialty families). Review `by_family.csv` after each run and adjust the classifier if you see a mislabeled family.

---

## 4. The script (CloudShell version)

Save this as `intel_spend.sh`.

```bash
#!/usr/bin/env bash
# intel_spend.sh - Intel compute spend for the current AWS account (CloudShell).
#
# Usage:
#   ./intel_spend.sh [START] [END]
#     START, END: YYYY-MM-DD (END is exclusive)
#     Default: the last 12 full calendar months.
#
# Output: ./intel_spend_out/
set -euo pipefail

# ---- Date range -----------------------------------------------------------
# CloudShell (Amazon Linux) has GNU date; the fallback handles macOS locally.
START="${1:-$(date -u -d '12 months ago' +%Y-%m-01 2>/dev/null || date -u -v-12m +%Y-%m-01)}"
END="${2:-$(date -u +%Y-%m-01)}"

OUT="intel_spend_out"
mkdir -p "$OUT"
RAW="$OUT/raw.csv"

# ---- Identify the account -------------------------------------------------
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account: $ACCOUNT_ID   Period: $START -> $END" >&2

# ---- Pull cost data from Cost Explorer ------------------------------------
echo "account_id,month,service,instance_type,cost" > "$RAW"

TOKEN=""
while :; do
  ARGS=(ce get-cost-and-usage
        --region us-east-1
        --time-period "Start=$START,End=$END"
        --granularity MONTHLY
        --metrics UnblendedCost
        --group-by Type=DIMENSION,Key=SERVICE Type=DIMENSION,Key=INSTANCE_TYPE
        --output json)
  [ -n "$TOKEN" ] && ARGS+=(--next-page-token "$TOKEN")

  RESP=$(aws "${ARGS[@]}")

  echo "$RESP" | jq -r --arg a "$ACCOUNT_ID" '
    .ResultsByTime[] | .TimePeriod.Start as $m | .Groups[]
    | select(.Keys[1] != "" and .Keys[1] != "NoInstanceType")
    | [$a, $m, .Keys[0], .Keys[1], .Metrics.UnblendedCost.Amount] | @csv' >> "$RAW"

  TOKEN=$(echo "$RESP" | jq -r '.NextPageToken // empty')
  [ -z "$TOKEN" ] && break
done

ROWS=$(( $(wc -l < "$RAW") - 1 ))
if [ "$ROWS" -le 0 ]; then
  echo "No instance-type cost data found for this period." >&2
  exit 0
fi

# ---- Classify and summarize ----------------------------------------------
python3 - "$RAW" "$OUT" <<'PYEOF'
import csv, re, sys, collections

raw, out = sys.argv[1], sys.argv[2]

ACCEL_PREFIXES = ("trn", "inf", "dl", "vt")      # AWS silicon / accelerators
GPU_LETTER_FAMILIES = {"p", "g", "f"}            # GPU / FPGA letter-only families

def extract_family(itype):
    """Pull the family token (e.g. m6i, c7g, r5n) out of strings such as
    m6i.large, db.m6i.large, cache.r7g.large, ml.m5.xlarge, m5.large.search."""
    for part in itype.lower().split("."):
        if re.match(r"^[a-z]+\d+[a-z]*(-[a-z0-9]+)?$", part):
            return part
    return None

def classify(itype):
    fam = extract_family(itype)
    if not fam:
        return "Other", itype
    m = re.match(r"^([a-z]+)(\d+)([a-z]*)", fam)
    letters, gen, suffix = m.groups()
    if letters.startswith(ACCEL_PREFIXES) or letters in GPU_LETTER_FAMILIES:
        return "Other", fam
    if "g" in suffix:
        return "Graviton", fam
    if "a" in suffix:
        return "AMD", fam
    return "Intel", fam

rows = list(csv.DictReader(open(raw)))
for r in rows:
    r["cost"] = float(r["cost"])
    r["vendor"], r["family"] = classify(r["instance_type"])

def write_summary(name, keys):
    agg = collections.defaultdict(float)
    for r in rows:
        agg[tuple(r[k] for k in keys)] += r["cost"]
    with open(f"{out}/{name}.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(keys + ["cost"])
        for k, v in sorted(agg.items()):
            w.writerow(list(k) + [round(v, 2)])

write_summary("by_vendor",          ["account_id", "vendor"])
write_summary("by_service_vendor",  ["account_id", "service", "vendor"])
write_summary("by_month_vendor",    ["account_id", "month", "vendor"])
write_summary("by_family",          ["account_id", "vendor", "family"])

with open(f"{out}/classified.csv", "w", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)

totals = collections.defaultdict(float)
for r in rows:
    totals[r["vendor"]] += r["cost"]

print("\n=== Spend by CPU vendor (instance-typed usage only) ===")
for k, v in sorted(totals.items(), key=lambda x: -x[1]):
    print(f"{k:10s} ${v:,.2f}")
PYEOF

echo >&2
echo "Output written to $OUT/ :" >&2
ls -1 "$OUT" >&2
```

---

## 5. Walkthrough: running it in CloudShell

1. **Sign in** to the AWS Console for the account you want to query.
2. **Open CloudShell** by clicking the terminal icon in the top navigation bar (or search for "CloudShell"). Wait for the prompt to appear.
3. **Create the script.** Either:
   - Use **Actions → Upload file** and upload `intel_spend.sh`, or
   - Paste it directly:
     ```bash
     nano intel_spend.sh
     ```
     Paste the contents, press `Ctrl+O`, `Enter`, then `Ctrl+X`.
4. **Make it executable:**
   ```bash
   chmod +x intel_spend.sh
   ```
5. **Run it.** For a specific range (end date is exclusive, so this covers the 12 full months from November 2025 through October 2026):
   ```bash
   ./intel_spend.sh 2025-11-01 2026-11-01
   ```
   Or use the default (last 12 full calendar months):
   ```bash
   ./intel_spend.sh
   ```
6. **Read the console summary**, for example:
   ```
   === Spend by CPU vendor (instance-typed usage only) ===
   Intel      $1,234.56
   Graviton   $210.00
   AMD        $98.40
   ```
7. **Download the results.** Choose **Actions → Download file** and enter the path, for example:
   ```
   intel_spend_out/classified.csv
   ```
   Repeat for `by_vendor.csv` or any other output file you need. To grab everything at once:
   ```bash
   zip -r intel_spend_out.zip intel_spend_out
   ```
   then download `intel_spend_out.zip`.

---

## 6. Output files

| File | Contents |
|---|---|
| `raw.csv` | Unclassified rows exactly as returned by Cost Explorer |
| `classified.csv` | Every row with `vendor` and `family` added; the best file for merging across accounts |
| `by_vendor.csv` | Total cost per CPU vendor |
| `by_service_vendor.csv` | Cost per service per vendor (for example, how much of the Intel spend is RDS vs. EC2) |
| `by_month_vendor.csv` | Monthly trend per vendor |
| `by_family.csv` | Cost per instance family; use it to sanity-check the classifier |

---

## 7. Multiple accounts

Without Organizations, each account has its own Cost Explorer. Repeat the walkthrough in each account, then combine the results.

1. Run the script in each account and download its `classified.csv`.
2. Rename each file with the account ID, for example `classified_111122223333.csv`.
3. Combine them locally. The simplest option is a short Python script:

```python
import glob, pandas as pd

df = pd.concat(
    (pd.read_csv(f) for f in glob.glob("classified_*.csv")),
    ignore_index=True,
)

print(df.groupby("vendor")["cost"].sum().round(2))
print(df.groupby(["account_id", "vendor"])["cost"].sum().round(2))

df.to_csv("all_accounts_classified.csv", index=False)
```

> If you manage more than a handful of accounts, consider setting up **IAM Identity Center** and `aws sso login` with named profiles. That lets a single machine loop over all accounts without opening CloudShell in each one. Adopting AWS Organizations would also give you consolidated billing and one Cost Explorer view for every account.

---

## 8. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `AccessDeniedException ... ce:GetCostAndUsage` | Your IAM identity lacks Cost Explorer permission. Ask an admin to add `ce:GetCostAndUsage`. Cost Explorer access may also be blocked at the account level for IAM users (the root user can enable "IAM user and role access to Billing information" in Account settings). |
| `DataUnavailableException` | Cost Explorer was only just enabled. Wait up to 24 hours. |
| `No instance-type cost data found` | The period has no instance-typed usage, or the date range is wrong. Check that `END` is after `START`. |
| `Start date ... cannot be more than 12 months ago` | Default Cost Explorer retention is 13 months (14 with the multi-year option enabled). Shorten the range. |
| `date: invalid option` on a Mac | Run `date -v-12m`, or pass explicit dates as arguments. CloudShell itself is not affected. |
| A family is mislabeled | Edit `classify()` in the script. Check `by_family.csv` for the offending family. |
| `command not found: jq` | Not expected in CloudShell. Run `sudo yum install -y jq` if it happens. |

---

## 9. Going further: exact accuracy

The instance-name heuristic cannot see Lambda or Fargate CPU vendors, and it relies on naming conventions. For exact results:

1. Create a **CUR 2.0 Data Export** in the account (Billing and Cost Management → Data Exports), including the columns `product_physical_processor`, `product_instance_type`, `product_servicecode`, `line_item_usage_account_id`, and `line_item_unblended_cost`.
2. Query it with Athena or DuckDB:

```sql
SELECT line_item_usage_account_id,
       product_servicecode,
       date_trunc('month', line_item_usage_start_date) AS month,
       sum(line_item_unblended_cost)                  AS cost
FROM   cur
WHERE  product_physical_processor LIKE 'Intel%'
GROUP  BY 1, 2, 3
ORDER  BY month, cost DESC;
```

`product_physical_processor` contains values such as `Intel Xeon Platinum 8488C`, `AMD EPYC 9R14`, and `AWS Graviton3`, so no name-based guessing is needed.

---

## 10. Cost of running this

Each Cost Explorer API request costs **$0.01**. A normal run makes one request (a few if the results paginate), so each account costs roughly a cent.
