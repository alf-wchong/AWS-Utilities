# Self-Hosted Git Server: Architecture & Deployment

A personal, self-maintained, GitHub-like git server — running on infrastructure
you control, at a cost of roughly **$1/month**, that only runs when you're
actually using it.

## Purpose

GitHub.com is owned by Microsoft. For personal or sensitive projects, that
means your source code, issue text, and metadata live on infrastructure
governed by a third party's terms of service, subject to their data handling
policies, AI-training opt-outs, and outage/account-suspension risk that's
entirely outside your control.

This project stands up **Forgejo** — a community-governed, open-source,
GitHub-like git host — on your own AWS account. You get repos, issues, pull
requests, a web UI, and markdown rendering that's functionally equivalent to
GitHub.com for solo/small-scale use, with your data staying on infrastructure
you own.

**Non-goals:** this is not a high-availability, multi-user, always-on
platform. It's sized for a single user (or a handful of collaborators) who
uses the server in occasional, bursty sessions — a few times a week, about an
hour at a time.

## Architecture Overview

```
                    ┌─────────────────┐
   Your device ───▶ │  DuckDNS (free) │  resolves mygitea.duckdns.org
                    └────────┬────────┘         to current public IP
                             │
                             ▼
                    ┌──────────────────────────────┐
                    │   EC2 instance (t4g.micro)    │
                    │  rootless Podman, user "forgejo" │
                    │  ┌────────────────────────┐  │
                    │  │ Caddy (reverse proxy)   │  │  :443 only (TLS-ALPN-01)
                    │  │  - auto Let's Encrypt   │  │
                    │  └───────────┬────────────┘  │
                    │              ▼                │
                    │  ┌────────────────────────┐  │
                    │  │ Forgejo (git server)    │  │  :3000 (web), :2222 (ssh)
                    │  │  - SQLite               │  │
                    │  └────────────────────────┘  │
                    │         EBS gp3 volume        │  (persists across stop/start)
                    └──────────────────────────────┘
                             ▲
                             │ start / stop
                    ┌────────┴─────────┐
                    │ Lambda Function   │  triggered by you (phone shortcut,
                    │ URLs (start/stop) │  curl, bookmark) with a shared secret
                    └───────────────────┘
```

**Key design decisions:**

| Decision | Why |
|---|---|
| Forgejo over Gitea | Community-governed fork; same tech, better fit for the "no single corporate steward" goal |
| SQLite over Postgres | Single-user, low-concurrency workload; avoids running/paying for a second service |
| Stopped-by-default EC2 | You use this a few times a week — paying for 24/7 compute makes no sense |
| Rootless Podman over Docker | No persistent root daemon; containers run as an unprivileged `forgejo` user with systemd Quadlet units managing them - smaller attack surface on a box with 80/443 open to the internet |
| Amazon Linux 2023 | Minimal, fast-booting (matters for cold-start time), native `dnf install podman`, SELinux not enforcing by default so no extra volume-label friction |
| Caddy over nginx+certbot | Caddy issues and renews Let's Encrypt certs automatically; no cron jobs, no manual renewal |
| TLS-ALPN-01 instead of HTTP-01, port 80 closed | Let's Encrypt's automated validation only runs over port 80 (HTTP-01) or port 443 (TLS-ALPN-01) - there's no free/automatic option on a non-standard port. Since 443 is already open for real traffic, using TLS-ALPN-01 avoids opening 80 at all, one fewer port exposed for no loss of function |
| DuckDNS over Route 53 | Free; no domain purchase; fine for a personal tool with a changing IP |
| Lambda Function URLs over API Gateway | No extra service, no extra cost, shared-secret check is enough for this threat model |
| No Elastic IP | EIPs attached-but-idle are free, but an EIP held while the instance is *stopped* is billed; DuckDNS updates on every boot instead |
| Start Lambda rewrites the SSH security group rule | Your ISP rotates your public IP every 8-12 hours, so a static allowlisted CIDR would lock you out. The start call itself arrives from your current IP - the Lambda uses that to replace the SSH rule on every start |
| Stop Lambda revokes the SSH rule entirely | Nothing is listening for SSH while stopped anyway, and it avoids leaving a stale allowlisted IP sitting on the security group between sessions |

## Cost Estimate (validated against AWS's live pricing pages, Aug 2026, us-east-1)

Your account has already used the 12-month free tier, so this assumes
**on-demand pricing throughout** — no free-tier credit applied.

| Item | Rate (us-east-1, on-demand) | Usage assumption | Monthly cost |
|---|---|---|---|
| EC2 `t4g.micro` | $0.0084/hr | ~2 sessions/week × 1.5 hrs (incl. buffer) ≈ 13 hrs/mo | **~$0.11** |
| EBS `gp3` volume, 10 GB | $0.08/GB-month | Billed while it exists, running or stopped | **$0.80** |
| Lambda (start/stop triggers) | Free tier: 1M requests + 400,000 GB-seconds/month, perpetual (not the 12-mo new-account tier) | ~10–20 invocations/month | **$0.00** |
| Lambda Function URLs | No additional charge beyond Lambda invocation | — | **$0.00** |
| Data transfer out | First 100 GB/month free | Small repos, occasional pushes/pulls | **$0.00** (expected) |
| DuckDNS | Free service | — | **$0.00** |
| Let's Encrypt (via Caddy) | Free | — | **$0.00** |
| **Total** | | | **≈ $0.91–$1.00/month** |

The only line item that doesn't disappear when you're not using it is the EBS
volume ($0.80/mo for 10GB) — that's the actual floor cost of "durable git
storage that survives a stop/start cycle."

> Note: this table reflects list prices pulled from AWS's on-demand pricing
> page and EBS pricing page as of today. For a config-specific, shareable
> estimate, run these same inputs through the [AWS Pricing
> Calculator](https://calculator.aws) directly — I can't generate a saved
> calculator link on your behalf, only validate the underlying rates.

## Prerequisites

- An AWS account (existing is fine — this assumes no free-tier credit)
- IAM permissions to create: EC2 instances, security groups, Lambda
  functions + Function URLs, IAM roles
- A [DuckDNS](https://www.duckdns.org) account — free, sign in with
  GitHub/Google/etc., and register a subdomain (e.g. `mygitea`)
- Your DuckDNS token (found on the DuckDNS dashboard after login)
- An existing EC2 key pair in `us-east-1` (or create one first —
  `aws ec2 create-key-pair --key-name my-key --query 'KeyMaterial' --output text > my-key.pem`)
- Your current public IP, for restricting SSH access
  (`curl https://checkip.amazonaws.com`)
- AWS CLI configured locally

## Step-by-Step Deployment

### 1. Register your DuckDNS subdomain
Log into DuckDNS, add a subdomain (e.g. `mygitea`), note the token from the
top of the dashboard. You don't need to set an IP yet — the server updates it
on every boot.

### 2. Deploy the CloudFormation stack
```bash
aws cloudformation create-stack \
  --stack-name forgejo-server \
  --template-body file://forgejo-stack.yaml \
  --capabilities CAPABILITY_IAM \
  --parameters \
    ParameterKey=DuckDnsSubdomain,ParameterValue=mygitea \
    ParameterKey=DuckDnsToken,ParameterValue=YOUR_DUCKDNS_TOKEN \
    ParameterKey=KeyPairName,ParameterValue=my-key \
    ParameterKey=AllowedSshCidr,ParameterValue=YOUR_IP/32 \
    ParameterKey=StartStopSharedSecret,ParameterValue=$(openssl rand -hex 24)
```
Save that shared secret somewhere — you'll need it to trigger start/stop.

### 3. Wait for first boot
The instance installs Podman, creates an unprivileged `forgejo` user, writes
the Quadlet unit files, and starts the Forgejo + Caddy containers under that
user's systemd session automatically. Give it 2-3 minutes on first launch.
Check progress via SSH if curious:
```bash
ssh -i my-key.pem ec2-user@<InstancePublicIp-from-stack-output>
sudo machinectl shell forgejo@ /bin/bash -c \
  'systemctl --user status forgejo.service caddy.service'
sudo machinectl shell forgejo@ /bin/bash -c \
  'journalctl --user -u forgejo.service -u caddy.service -f'
```

### 4. Verify DNS + TLS
```bash
dig mygitea.duckdns.org
curl -I https://mygitea.duckdns.org
```
Caddy requests a Let's Encrypt cert automatically on first HTTPS connection
to the domain, using the TLS-ALPN-01 challenge over port 443 — no port 80
needed, which is why the security group only opens 443.

### 5. Finish the Forgejo setup wizard
Visit `https://mygitea.duckdns.org` and complete the first-run admin account
setup in the web UI.

### 6. Push your first repo
```bash
git remote add origin https://mygitea.duckdns.org/youruser/yourrepo.git
git push -u origin main
```
Or over SSH via port 2222:
```bash
git remote add origin ssh://git@mygitea.duckdns.org:2222/youruser/yourrepo.git
```

### 7. Starting and stopping the instance
The stack outputs two Function URLs. Trigger them with the shared secret:
```bash
curl -X POST "$START_FUNCTION_URL" -d '{"secret":"YOUR_SHARED_SECRET"}'
curl -X POST "$STOP_FUNCTION_URL"  -d '{"secret":"YOUR_SHARED_SECRET"}'
```
The start call also rewrites the SSH security group rule to allow only the
IP it was called from - so whichever network you're on when you hit "start"
becomes the only one that can SSH in until the next start. If your ISP
rotates your IP mid-session, SSH access won't follow it (the web/git-over-
https ports don't have this restriction, so that traffic is unaffected).

Save these as a phone shortcut / browser bookmarklet for one-tap start before
a session.

## Operations

- **Logs:**
  `sudo machinectl shell forgejo@ /bin/bash -c 'journalctl --user -u forgejo.service -u caddy.service -f'`
- **Backups:** take a manual EBS snapshot before major changes
  (`aws ec2 create-snapshot --volume-id <id>`), or schedule one via Data
  Lifecycle Manager if you want it automatic. Also consider `forgejo dump`
  (run via `podman exec forgejo forgejo dump`) for a portable
  application-level backup independent of EBS.
- **Upgrading Forgejo:** bump the `Image=` tag in
  `/home/forgejo/.config/containers/systemd/forgejo.container`, then as the
  `forgejo` user: `systemctl --user daemon-reload && systemctl --user restart forgejo.service`
- **Stopping without the Lambda:** `aws ec2 stop-instances --instance-ids <id>`
  works too, but skips the SSH-rule cleanup the Lambda does — the Lambda is
  the preferred path, not just a convenience.

## Security Notes

- SSH is restricted to a single `/32` that's rewritten on every start to
  whichever IP called the start Lambda, and revoked entirely by the stop
  Lambda - so the rule only exists while the instance is actually running.
  HTTPS (443) is open — required for git-over-https, the web UI, and Caddy's
  TLS-ALPN-01 Let's Encrypt challenge. Port 80 is closed entirely; it isn't
  needed since TLS-ALPN-01 validates over 443. The `AllowedSshCidr` parameter
  only seeds the *initial* SSH rule at stack creation.
- Because the SSH rule now tracks whoever calls "start," anyone who has the
  shared secret can point SSH access at their own IP by calling the start
  endpoint - the shared secret is effectively the real gate on SSH access,
  not the CIDR. Treat the shared secret with the same care as an SSH key.
- The start/stop Lambda Function URLs are **publicly reachable but
  secret-gated** — anyone can hit the endpoint, but without the shared secret
  they get a 403. This is adequate for "stop a random person from turning on
  my EC2 instance," not for a system with real stakes. If that matters more
  later, move to `AuthType: AWS_IAM` on the Function URL.
- The DuckDNS token and shared secret are passed as CloudFormation
  `NoEcho` parameters (hidden from console/API output) but do land in plain
  text inside the instance's UserData, which is readable by anyone with
  `ec2:DescribeInstanceAttribute` on this instance. Scope IAM accordingly.
- SQLite is fine here specifically *because* this is single-user and
  low-concurrency. Don't add more concurrent users without moving to
  Postgres.

## Known Limitations

- No high availability — one instance, one volume, no failover.
- Public IP changes on every stop/start; DuckDNS is updated on boot, but
  there's a brief window (a few seconds) after boot before DNS is current.
- Cold start from a stopped instance is roughly 30-60 seconds, plus another
  10-20 seconds for the compose stack and first TLS handshake.
- Background maintenance tasks that a long-running server would normally
  do on a schedule (session cleanup, mirror sync) only run while the
  instance is actually up.
- This UserData script is a first draft, not yet deployed/tested end-to-end.
  Podman Quadlet requires a reasonably recent Podman version (≥4.4) - verify
  what Amazon Linux 2023's `dnf` repo provides at deploy time, and treat the
  `machinectl shell` / `loginctl enable-linger` sequence as the part most
  worth watching on first boot, since lingering + rootless user services at
  boot time is the trickiest part of this setup to get exactly right.
