#!/bin/bash
APP_CMD="/path/to/your-app"
while true; do
  # Refresh creds before each start
  source /path/to/setTempCreds.sh

  # Launch app in background, capture its PID
  $APP_CMD &
  APP_PID=$!

  # Monitor expiry
  while kill -0 $APP_PID 2>/dev/null; do
    EXPIRY=$(cat /tmp/tempAWSCredExpiry)
    EXP_SEC=$(date -d "$EXPIRY" +%s)
    NOW_SEC=$(date +%s)
    # If within 6-minute buffer, restart
    if (( EXP_SEC - NOW_SEC <= 360 )); then
      kill $APP_PID
      break
    fi
    sleep 30
  done

  # Wait for app to fully exit before next iteration
  wait $APP_PID 2>/dev/null
done
