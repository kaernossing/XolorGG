#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Execute ON ANDROID using adb -s SELECTED_DEVICE shell sh PATH_TO_SCRIPT.
set -eu
state=$(content call --uri content://io.github.joeyandu.colorcamera.bridge --method status)
case "$state" in
  *requested=true*) ;;
  *requested=false*) echo 'Enable ColorCamera in its app before activation.'; exit 2 ;;
  *) echo 'Cannot read ColorCamera state; activation refused.'; exit 3 ;;
esac
if pidof colorgoogle-search >/dev/null; then
  echo 'ColorGoogle agent is running. Turn off its main service first.'; exit 4
fi
if pidof colorcamera-agent >/dev/null; then
  echo 'ColorCamera agent already exists; it will not be restarted.'
  case "$state" in *active=true*) case "$state" in *version=10000*) exit 0 ;; esac ;; esac
  echo 'Existing agent is not ready or has a different version. Stop it in ColorCamera before retrying.'; exit 6
fi
apk=$(pm path --user 0 io.github.joeyandu.colorcamera | sed -n 's/^package://p' | head -n 1)
test -r "$apk"
export CLASSPATH="$apk"
# Full wrapper diagnostics are rotated once; structured agent events have a separate 128 KiB cap.
if test -f /data/local/tmp/colorcamera-start.log; then mv /data/local/tmp/colorcamera-start.log /data/local/tmp/colorcamera-start.log.1; fi
nohup setsid app_process / --nice-name=colorcamera-agent dev.evoker.homeholdcts.CameraAgent </dev/null >/data/local/tmp/colorcamera-start.log 2>&1 &
sleep 3
state=$(content call --uri content://io.github.joeyandu.colorcamera.bridge --method status)
echo "$state"
if ! pidof colorcamera-agent >/dev/null; then
  echo 'Activation failed. See /data/local/tmp/colorcamera-start.log and colorcamera-events.log.'
  cat /data/local/tmp/colorcamera-start.log
  exit 5
fi
case "$state" in
  *active=true*) exit 0 ;;
  *) echo 'Agent exists but is not ready; inspect its status before retrying.'; exit 6 ;;
esac
