# 🌀 refresh-aws-credentials.sh

A lightweight Bash utility that **automatically refreshes AWS temporary credentials** (such as those obtained via `aws sts assume-role`) and updates your AWS CLI configuration.  

This script is designed for users who work with **short-lived AWS session credentials** and want to ensure they remain valid without manual intervention.

---

## 📋 Overview

The `refresh-aws-credentials.sh` script checks whether your current AWS credentials are nearing expiration.  
If they are, it automatically assumes a role (or fetches new credentials via AWS STS) and writes them back into your AWS credentials file.

It is typically used with IAM roles, MFA, or SSO setups where temporary credentials are issued and must be periodically refreshed.

---

## ⚙️ Features

- 🔄 Automatically refreshes temporary AWS credentials before they expire  
- 🕓 Detects credential expiration and remaining lifetime  
- 🧩 Supports custom AWS profiles and credentials files  
- 🔐 Uses `aws sts assume-role` (or equivalent) to request new session tokens  
- ✍️ Writes refreshed credentials into `~/.aws/credentials` or a specified file  
- 🪶 Lightweight and dependency-free (only requires AWS CLI + Bash)

---

## 🚀 Usage

### 1️⃣ Prerequisites
- **AWS CLI v2+** installed and configured  
- A valid IAM role that can be assumed via `aws sts assume-role`  
- Existing AWS CLI profile with permissions to assume the target role

---

### 2️⃣ Run the Script

```bash
./refresh-aws-credentials.sh
```

---

## 🧠 Example
# Example usage with custom profile and role
```
AWS_PROFILE=my-admin \
ROLE_ARN=arn:aws:iam::123456789012:role/TemporaryAccessRole \
SESSION_NAME=myTempSession \
./refresh-aws-credentials.sh
```

After running, your ~/.aws/credentials file will be updated with fresh temporary credentials under the specified profile.

---

## 🔧 Environment Variables

| Variable           | Description                                 | Default              |
| ------------------ | ------------------------------------------- | -------------------- |
| `AWS_PROFILE`      | AWS CLI profile to refresh                  | `default`            |
| `ROLE_ARN`         | IAM role ARN to assume                      | *(required)*         |
| `SESSION_NAME`     | Session name for the STS request            | `autoRefreshSession` |
| `DURATION_SECONDS` | Requested session duration (seconds)        | `3600`               |
| `CREDENTIALS_FILE` | Target AWS credentials file                 | `~/.aws/credentials` |
| `FORCE_REFRESH`    | Force refresh even if credentials are valid | `false`              |

---

## 🧰 Example Cron Job

To automatically refresh credentials every hour:

```
0 * * * * /path/to/refresh-aws-credentials.sh >> /var/log/aws-refresh.log 2>&1
```

---

## 🪪 License

This script is released under the MIT License.
See [LICENSE](../LICENSE)
 for details.
