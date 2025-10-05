# Auto-Refreshing AWS EC2 Temporary Credentials

This repository provides two scripts to automatically fetch and maintain valid AWS EC2 instance metadata and temporary IAM role credentials. Together, they ensure your applications always run with up-to-date `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN`.

## Components

1. [**setTempCreds.sh**](setTempCreds.sh)
    - Fetches EC2 metadata (region, AZ, instance ID/type, network details) via IMDSv2 (with IMDSv1 fallback).
    - Retrieves temporary IAM credentials from the instance’s role, using `jq` to parse JSON.
    - Stores the credential expiration timestamp in `/tmp/tempAWSCredExpiry` (ISO format).
    - Exports environment variables:

```bash
AWS_REGION, AWS_DEFAULT_REGION
AWS_AVAILABILITY_ZONE, AWS_INSTANCE_ID, AWS_INSTANCE_TYPE
AWS_PRIVATE_IP, AWS_PUBLIC_IP
AWS_PRIVATE_HOSTNAME, AWS_PUBLIC_HOSTNAME
AWS_MAC, AWS_VPC_ID, AWS_SUBNET_ID
AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN
```

    - Validates credentials with `aws sts get-caller-identity`.
    - Logs errors and refresh events to `/tmp/tempAWSCredRefresh.log`.
2. [**tokenExpiryWatchdog.sh**](tokenExpiryWatchdog.sh)
    - Acts as a supervisor loop for long-running applications.
    - Sources `setTempCreds.sh` before each launch to guarantee fresh credentials.
    - Monitors `/tmp/tempAWSCredExpiry` every 30 seconds.
    - If credentials are within a 6-minute expiry buffer, it gracefully restarts your application so it inherits new environment variables.
    - [Example wrapper snippet](tokenExpiryWatchdog.sh).



## Why These Steps Are Necessary

- **Environment Variables Are Static at Launch**
Any process reads `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` only once at startup. Simply exporting new values into your shell does not update a running process’s environment.
- **Automatic Credential Expiry**
Temporary IAM credentials expire (usually every hour). Without a refresh mechanism, your long-running application will eventually lose permissions and fail API calls.
- **Reliable Restart Logic**
The watchdog script ensures your app is restarted just before credentials expire, seamlessly inheriting fresh values without manual intervention.
- **Systemd Supervision**
Running the watchdog under systemd provides resilience:
    - Automatically starts on boot
    - Restarts on failure
    - Centralized logging and service management


## Systemd Service Setup

1. Copy both scripts into `/usr/local/bin/` and make executable:

```bash
sudo cp setTempCreds.sh tokenExpiryWatchdog.sh /usr/local/bin/
sudo chmod +x /usr/local/bin/*.sh
```

2. Create a [systemd unit file](systemd_service) at `/etc/systemd/system/aws-temp-creds.service`:



3. Reload systemd, enable, and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable aws-temp-creds.service
sudo systemctl start aws-temp-creds.service
```

4. Verify status and logs:

```bash
sudo systemctl status aws-temp-creds.service
sudo journalctl -u aws-temp-creds.service -f
```


With this setup, your long-running application will always inherit valid AWS credentials, and refresh cycles happen transparently in the background.

