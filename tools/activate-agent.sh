#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Canonical starter, embedded in the APK by Gradle. Run on Android via adb shell.
set -eu
state=$(content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status --arg compact)
case "$state" in
  *runEnabled=true*) ;;
  *runEnabled=false*) echo 'Main switch is off; enable ColorGoogle before activation.'; exit 2 ;;
  *) echo 'Cannot read main switch; activation refused.'; exit 3 ;;
esac
if pidof colorgoogle-search >/dev/null; then
  echo 'Agent already exists; not stopping it. Check version and connection status below.'
  content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status
  exit 0
fi
if pidof colorcamera-agent >/dev/null; then
  echo 'ColorCamera is running; turn it off before activating ColorGoogle.'; exit 4
fi
apk=$(pm path --user 0 dev.evoker.homeholdcts | sed -n 's/^package://p' | head -n 1)
test -r "$apk"
export CLASSPATH="$apk"
nohup setsid app_process / --nice-name=colorgoogle-search dev.evoker.homeholdcts.DesktopSearchAgent </dev/null >/data/local/tmp/colorgoogle-search.log 2>&1 &
sleep 2
content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status
