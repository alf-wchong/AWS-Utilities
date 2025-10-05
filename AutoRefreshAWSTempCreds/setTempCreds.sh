#!/bin/bash

# Log file for credential refresh errors
ERROR_LOG="/tmp/tempAWSCredRefresh.log"
EXPIRY_FILE="/tmp/tempAWSCredExpiry"

# Function to retrieve and export AWS credentials
get_aws_credentials() {
  local token=$1
  local meta_base="http://169.254.169.254/latest/meta-data/iam/security-credentials"
  local role
  local creds_json
  local access_key
  local secret_key
  local session_token
  local expiration

  # Step 1: Get IAM role name
  if ! role=$(curl -H "X-aws-ec2-metadata-token: $token" -s $meta_base 2>/dev/null); then
    echo "$(date -Is) ERROR: Unable to fetch IAM role name" >> "$ERROR_LOG"
    AWS_ACCESS_KEY_ID="" AWS_SECRET_ACCESS_KEY="" AWS_SESSION_TOKEN=""
    return 1
  fi

  # Step 2: Get credentials JSON
  if ! creds_json=$(curl -H "X-aws-ec2-metadata-token: $token" -s "$meta_base/$role" 2>/dev/null); then
    echo "$(date -Is) ERROR: Unable to fetch credentials JSON for role $role" >> "$ERROR_LOG"
    AWS_ACCESS_KEY_ID="" AWS_SECRET_ACCESS_KEY="" AWS_SESSION_TOKEN=""
    return 1
  fi

  # Parse JSON using jq
  access_key=$(echo "$creds_json" | jq -r .AccessKeyId)
  secret_key=$(echo "$creds_json" | jq -r .SecretAccessKey)
  session_token=$(echo "$creds_json" | jq -r .Token)
  expiration=$(echo "$creds_json" | jq -r .Expiration)

  # Export credentials
  export AWS_ACCESS_KEY_ID="$access_key"
  export AWS_SECRET_ACCESS_KEY="$secret_key"
  export AWS_SESSION_TOKEN="$session_token"

  # Store expiration timestamp (ISO format)
  echo "$expiration" > "$EXPIRY_FILE"
}

# Function to check and refresh credentials if needed
refresh_credentials_if_needed() {
  local now expiry buffer_seconds=360
  now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # If no expiry file, fetch credentials
  if [ ! -f "$EXPIRY_FILE" ]; then
    get_aws_credentials "$TOKEN" || return
    return
  fi

  expiry=$(cat "$EXPIRY_FILE")
  # Convert ISO to seconds since epoch
  expiry_sec=$(date -d "$expiry" +%s)
  now_sec=$(date -d "$now" +%s)

  # If expiring within buffer or invalid session, refresh
  if [ $((expiry_sec - now_sec)) -le $buffer_seconds ] || ! aws sts get-caller-identity --output json >/dev/null 2>&1; then
    echo "$(date -Is) INFO: Refreshing AWS credentials" >> "$ERROR_LOG"
    get_aws_credentials "$TOKEN" || return
  fi
}

# Try to get EC2 metadata token (IMDSv2)
TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" -s 2>/dev/null)

if [ -n "$TOKEN" ]; then
  # IMDSv2 block: retrieve metadata
  export AWS_REGION=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/placement/region 2>/dev/null)
  export AWS_AVAILABILITY_ZONE=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/placement/availability-zone 2>/dev/null)
  export AWS_INSTANCE_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null)
  export AWS_INSTANCE_TYPE=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/instance-type 2>/dev/null)
  export AWS_PRIVATE_IP=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/local-ipv4 2>/dev/null)
  export AWS_PUBLIC_IP=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null)
  export AWS_PRIVATE_HOSTNAME=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/local-hostname 2>/dev/null)
  export AWS_PUBLIC_HOSTNAME=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/public-hostname 2>/dev/null)
  export AWS_MAC=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/mac 2>/dev/null)
  export AWS_VPC_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/network/interfaces/macs/$AWS_MAC/vpc-id 2>/dev/null)
  export AWS_SUBNET_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" -s http://169.254.169.254/latest/meta-data/network/interfaces/macs/$AWS_MAC/subnet-id 2>/dev/null)
  export AWS_DEFAULT_REGION="$AWS_REGION"

  # Credential refresh logic
  refresh_credentials_if_needed

else
  # Fallback: IMDSv1 and AWS CLI region fallbacks
  if AWS_REGION=$(aws configure get region 2>/dev/null); then
    export AWS_REGION
  else
    export AWS_REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region 2>/dev/null)
  fi

  # Other metadata via IMDSv1
  export AWS_AVAILABILITY_ZONE=$(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone 2>/dev/null)
  export AWS_INSTANCE_ID=$(curl -s http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null)
  # ... (other metadata as in IMDSv2 block) ...
  export AWS_DEFAULT_REGION="$AWS_REGION"

  # Credential retrieval using IMDSv1
  TOKEN=""
  refresh_credentials_if_needed
fi
