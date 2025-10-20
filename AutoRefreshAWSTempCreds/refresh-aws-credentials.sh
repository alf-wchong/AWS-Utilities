#!/bin/bash

# AWS Credentials Refresh Script (User version)
CREDENTIALS_FILE="$HOME/.aws/credentials"
LOG_FILE="$HOME/aws-credentials-refresh.log"

# Function to log messages
log_message() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> "$LOG_FILE"
}

# Function to get credentials from metadata
get_credentials() {
    TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
    
    if [ -z "$TOKEN" ]; then
        log_message "ERROR: Failed to get IMDS token"
        return 1
    fi
    
    ROLE_NAME=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/iam/security-credentials/)
    
    if [ -z "$ROLE_NAME" ]; then
        log_message "ERROR: Failed to get role name"
        return 1
    fi
    
    CREDENTIALS=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/iam/security-credentials/$ROLE_NAME)
    
    if [ -z "$CREDENTIALS" ]; then
        log_message "ERROR: Failed to get credentials"
        return 1
    fi
    
    echo "$CREDENTIALS"
}

log_message "Starting credential refresh process"

CREDS=$(get_credentials)
if [ $? -ne 0 ]; then
    log_message "ERROR: Failed to retrieve credentials"
    exit 1
fi

ACCESS_KEY=$(echo "$CREDS" | python3 -c "import sys, json; print(json.load(sys.stdin)['AccessKeyId'])")
SECRET_KEY=$(echo "$CREDS" | python3 -c "import sys, json; print(json.load(sys.stdin)['SecretAccessKey'])")
SESSION_TOKEN=$(echo "$CREDS" | python3 -c "import sys, json; print(json.load(sys.stdin)['Token'])")
EXPIRATION=$(echo "$CREDS" | python3 -c "import sys, json; print(json.load(sys.stdin)['Expiration'])")

mkdir -p "$HOME/.aws"

cat > "$CREDENTIALS_FILE" << EOF
[default]
aws_access_key_id = $ACCESS_KEY
aws_secret_access_key = $SECRET_KEY
aws_session_token = $SESSION_TOKEN
# Expires: $EXPIRATION
EOF

chmod 600 "$CREDENTIALS_FILE"
 
