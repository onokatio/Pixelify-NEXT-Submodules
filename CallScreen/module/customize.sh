#!/system/bin/sh
[ "${API:-0}" -ge 26 ] || abort "CallScreen requires Android 8.0 or newer."
[ -f "$MODPATH/callscreen.jar" ] || abort "Missing CallScreen helper."

ui_print "- Reboot and unlock to apply Call Screen flags to the existing Google Phone app."
ui_print "- In Phone > Settings > Call Screen, tap Retry if Japanese audio needs downloading."
