#!/bin/sh
# Removes the ATS local networking exception (plain HTTP to the local api) from every
# build that is not Debug, so Release and Profile builds only allow HTTPS.
# Runs as the last build phase of the Runner target, on the processed Info.plist.
set -eu

if [ "${CONFIGURATION}" = "Debug" ]; then
  exit 0
fi

PLIST="${TARGET_BUILD_DIR}/${INFOPLIST_PATH}"
if /usr/libexec/PlistBuddy -c "Print :NSAppTransportSecurity:NSAllowsLocalNetworking" "${PLIST}" >/dev/null 2>&1; then
  /usr/libexec/PlistBuddy -c "Delete :NSAppTransportSecurity:NSAllowsLocalNetworking" "${PLIST}"
fi
# Drop the dictionary when the exception was its only entry.
if [ "$(/usr/libexec/PlistBuddy -c "Print :NSAppTransportSecurity" "${PLIST}" 2>/dev/null | wc -l | tr -d ' ')" = "2" ]; then
  /usr/libexec/PlistBuddy -c "Delete :NSAppTransportSecurity" "${PLIST}"
fi
