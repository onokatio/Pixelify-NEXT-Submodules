#!/system/bin/sh

case "$ARCH" in
    arm|arm64|x86|x64) ;;
    *) abort "No bundled sqlite3 for architecture $ARCH" ;;
esac

sqlite="$MODPATH/addon/sqlite3"
mv "$sqlite.$ARCH" "$sqlite" || abort "Bundled sqlite3 is missing for $ARCH"
set_perm "$sqlite" 0 0 0755
[ "$("$sqlite" -init /dev/null -batch :memory: 'SELECT 1;')" = 1 ] ||
    abort "Bundled sqlite3 failed its installation check"

rm -f "$MODPATH"/addon/sqlite3.*
ui_print "- Bundled sqlite3 is ready for $ARCH"
