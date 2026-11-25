# Detect Duplicate PoC Fund Requests per Opportunity

Quickly find AWS Partner Central **Proof of Concept (PoC)** fund requests that share the same **Opportunity ID**, using only the AWS CLI and `jq`.

A single Opportunity should normally back **one** PoC fund request. This guide shows how to list every PoC submission, group them by opportunity, and flag opportunities with multiple submissions or work-in-progress (draft) submissions.

## Prerequisites

- AWS CLI v2 with the `partnercentral-benefits` service (tested with `aws-cli/2.40.4`). Upgrade if the command is missing.
- [`jq`](https://jqlang.org/)
- AWS credentials for your Partner Central account with permission to read benefit applications (for example `partnercentral:ListBenefitApplications`).
- Region: `us-east-1`.

Verify the service is available:

```bash
aws partnercentral-benefits help | sed -n '/AVAILABLE COMMANDS/,$p'
```

## How statuses are treated

| Status | Treatment |
|---|---|
| `CANCELED` / `CANCELLED` | **Ignored.** Does not count. |
| `PENDING_SUBMISSION` created within the last `DRAFT_FRESHNESS` days | **Fresh draft.** Counts as a potential duplicate. |
| `PENDING_SUBMISSION` older than `DRAFT_FRESHNESS` days | **Stale draft.** Listed under `stale_drafts`, does not count. |
| Everything else (`IN_REVIEW`, `APPROVED`, `ACTION_REQUIRED`, ...) | **Counts**, regardless of age. |

`DRAFT_FRESHNESS` is a number of days (default suggestion: `30`). Set it to `0` to ignore all drafts, or a very large number (e.g. `3650`) to count every draft.

> Confirm the exact status strings in your account (see Step 2).

## Step 1: Find the PoC benefit ID

```bash
aws partnercentral-benefits list-benefits --catalog AWS --region us-east-1 \
  --query "BenefitSummaries[?Programs && contains(Programs,'POC')].[Id,Name]" \
  --output table
```

Example result:

| Id | Name |
|---|---|
| `ben-un8xx6xxxxqxxx` | Proof Of Concept Funding program |

A "Proof Of Concept Cash Claim" benefit may also exist. Include its ID if you want it in the check.

## Step 2: Export the PoC fund requests

```bash
BEN="ben-un8xx6xxxxqxxx"    # add more IDs, space-separated, if needed

aws partnercentral-benefits list-benefit-applications \
  --catalog AWS --region us-east-1 \
  --benefit-identifiers $BEN \
  --output json > apps.json
```

Sanity-check the statuses present:

```bash
jq -r '.BenefitApplicationSummaries[].Status' apps.json | sort | uniq -c
```

If `--benefit-identifiers` is rejected, run `aws partnercentral-benefits list-benefit-applications help` for the correct filter flag.

## Step 3: Report opportunities with multiple submissions

```bash
DRAFT_FRESHNESS=30   # days; drafts created within this window count as potential duplicates

jq --argjson days "$DRAFT_FRESHNESS" '
  (now - ($days * 86400)) as $cutoff
  | [.BenefitApplicationSummaries[]
     | {id: .Id, status: .Status, created: .CreatedAt,
        activity: .BenefitApplicationDetails.ActivityName,
        opps: ([.AssociatedResources[]? | select(test("/opportunity/")) | sub(".*/opportunity/";"")])}
     | select(.status | test("CANCEL") | not)
     | . + {fresh: ((.created | sub("\\.[0-9]+";"") | sub("\\+00:00$";"Z") | fromdateiso8601) >= $cutoff)}
     | . as $a | ($a.opps[]? // "NONE") as $o | $a + {opp: $o}]
  | group_by(.opp)
  | map({opp: .[0].opp,
         counting: (map(select(.status != "PENDING_SUBMISSION" or .fresh)) | sort_by(.created)),
         stale_drafts: (map(select(.status == "PENDING_SUBMISSION" and (.fresh | not))) | sort_by(.created))})
  | map(select(.opp != "NONE" and (.counting|length) > 1))
' apps.json
```

Each result contains:

- `opp`: the Opportunity ID
- `counting`: live submissions plus fresh drafts, oldest first (more than one means a **potential duplicate**)
- `stale_drafts`: older work-in-progress submissions on the same opportunity (noted only)

The `fresh` field is calculated for every row but only affects drafts.

## Step 4: Variations

**Every opportunity with more than one submission of any kind** (live, fresh or stale draft), to review everything in one report. Replace the final `select()` in Step 3 with:

```bash
| map(select(.opp != "NONE" and ((.counting|length) + (.stale_drafts|length)) > 1))
```

**Drafts with no opportunity attached** (cannot be duplicate-checked until one is linked):

```bash
jq '[.BenefitApplicationSummaries[]
     | select(.Status == "PENDING_SUBMISSION")
     | select(([.AssociatedResources[]? | select(test("/opportunity/"))] | length) == 0)
     | {id: .Id, created: .CreatedAt, activity: .BenefitApplicationDetails.ActivityName}]' apps.json
```

**CSV of everything for review:**

```bash
jq -r '.BenefitApplicationSummaries[]
  | [.Id, .Status, .CreatedAt, .UpdatedAt,
     (.BenefitApplicationDetails.ActivityName // ""),
     ([.AssociatedResources[]? | select(test("/opportunity/")) | sub(".*/opportunity/";"")] | join(";"))]
  | @csv' apps.json > poc_applications.csv
```

## Interpreting results

| Pattern | Likely meaning | Suggested action |
|---|---|---|
| Same activity name, 2+ live submissions | True duplicate or resubmission | Keep one; cancel or recall the other |
| Different activity names on one opportunity | Wrong opportunity attached | Re-associate the correct opportunity |
| 1 live submission + fresh draft | Likely the same effort started twice | Delete or cancel the draft |
| Drafts with no opportunity | Incomplete | Attach an opportunity or discard |

## Fixing duplicates

Check the required arguments first:

```bash
aws partnercentral-benefits cancel-benefit-application help
aws partnercentral-benefits recall-benefit-application help
```

- `recall-benefit-application`: pull back a submitted application that is still in review.
- `cancel-benefit-application`: cancel an application.

Confirm with AWS or your Partner Development Manager before cancelling an **APPROVED** application.

## Bonus: List opportunities eligible for a fund request

To pre-check which opportunities are candidates, list those before **Business Validation**:

```bash
aws partnercentral-selling list-opportunities \
  --catalog AWS --region us-east-1 \
  --lifecycle-stage "Prospect" "Qualified" "Technical Validation" \
  --query "OpportunitySummaries[].Id" --output text
```

Valid lifecycle stages: `Prospect`, `Qualified`, `Technical Validation`, `Business Validation`, `Committed`, `Launched`, `Closed Lost`.

## Notes and limitations

- `CreatedAt` is when the fund request record was created (UTC), not necessarily when it was submitted.
- Opportunity IDs come from the `AssociatedResources` ARNs (`.../opportunity/O1234567`).
- The CLI paginates automatically. If output looks truncated, use `--no-paginate` and loop with `--next-token`.
- The Partner Central fund request wizard may apply additional eligibility rules that are not exposed by the API.
