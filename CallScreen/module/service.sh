#!/system/bin/sh
MODDIR=${0%/*}
dialer=com.google.android.dialer
directory=/data/user/0/$dialer/files/phenotype
snapshot=$directory/$dialer
state=/data/adb/PixelifyCallScreen
umask 077

report() {
    /system/bin/log -t PixelifyCallScreen "$*"
}

helper() {
    CLASSPATH="$MODDIR/callscreen.jar" /system/bin/app_process /system/bin \
        org.pixelify.callscreen.Phenotype "$@"
}

has_snapshot() {
    [ -f "$snapshot" ] && [ ! -L "$snapshot" ] &&
        [ "$(readlink -f "$directory")" = "$(readlink -f /data/user/0)/$dialer/files/phenotype" ]
}

replace_snapshot() (
    has_snapshot || exit 1
    stage=$(mktemp "$directory/.callscreen.XXXXXX") || exit 1
    trap 'rm -f "$stage"' EXIT
    owner=$(stat -c '%u:%g' "$snapshot") || exit 1
    mode=$(stat -c '%a' "$snapshot") || exit 1
    label=$(/system/bin/ls -Zd "$snapshot" | cut -d ' ' -f 1)
    cp "$1" "$stage" &&
        chown "$owner" "$stage" &&
        chmod "$mode" "$stage" &&
        /system/bin/chcon "$label" "$stage" &&
        has_snapshot &&
        cmp -s "$2" "$snapshot" &&
        mv -f "$stage" "$snapshot"
)

checksum() {
    sha256sum "$1" | cut -d ' ' -f 1
}

apply_flags() (
    mkdir -p "$state" || exit 1
    work=$(mktemp -d "$state/.apply.XXXXXX") || exit 1
    trap 'rm -rf "$work"' EXIT
    cp "$snapshot" "$work/original" || exit 1
    if ! error=$(helper patch "$work/original" "$work/patched" 2>&1); then
        report "Snapshot was left untouched. $error"
        exit 1
    fi
    cmp -s "$work/original" "$work/patched" && exit 0

    backup="$state/$(checksum "$work/patched").pb"
    cp "$work/original" "$work/backup" &&
        mv -f "$work/backup" "$backup" &&
        replace_snapshot "$work/patched" "$work/original" || exit 1
    for old_backup in "$state"/*.pb; do
        [ "$old_backup" = "$backup" ] || rm -f "$old_backup"
    done
    report "Japanese Call Screen flags applied"
    /system/bin/am kill --user 0 "$dialer" >/dev/null 2>&1
)

restore_flags() (
    [ -d "$state" ] || exit 0
    # KernelSU removes modules before credential-encrypted app data is unlocked.
    until user_unlocked; do sleep 3; done
    work=$(mktemp -d "$state/.restore.XXXXXX") || exit 1
    trap 'rm -rf "$work"; rmdir "$state" 2>/dev/null || true' EXIT
    if has_snapshot; then
        cp "$snapshot" "$work/current" || exit 1
        backup="$state/$(checksum "$work/current").pb"
        if [ -f "$backup" ]; then
            replace_snapshot "$backup" "$work/current" || exit 1
            report "Restored previous Google Phone flags"
        fi
    fi
    rm -f "$state"/*.pb
)

user_unlocked() {
    [ "$(/system/bin/am get-started-user-state 0 2>/dev/null)" = RUNNING_UNLOCKED ]
}

if [ "$1" = --restore ]; then
    restore_flags </dev/null >/dev/null 2>&1 &
    exit 0
fi

report "Waiting for Google Phone configuration after first unlock"
while [ -d "$MODDIR" ] && [ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ]; do
    if [ "$(/system/bin/getprop sys.boot_completed)" = 1 ] && user_unlocked; then
        break
    fi
    sleep 3
done

last_state=
checked_paths=
compatible=false
while [ -d "$MODDIR" ] && [ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ]; do
    if has_snapshot; then
        country=$(/system/bin/getprop gsm.sim.operator.iso-country)
        current_state="$country $(stat -c '%i:%y:%s' "$snapshot" /data/system/packages.xml 2>/dev/null)"
        if [ "$current_state" != "$last_state" ]; then
            last_state=$current_state
            case ",$country," in
                *,jp,*)
                    if [ "$(/system/bin/cmd telecom get-default-dialer 2>/dev/null)" = "$dialer" ] &&
                        /system/bin/pm list packages -s --user 0 "$dialer" | grep -qx "package:$dialer"; then
                        paths=$(/system/bin/pm path --user 0 "$dialer" | sed -n 's/^package://p')
                        if [ "$paths" != "$checked_paths" ]; then
                            checked_paths=$paths
                            compatible=false
                            if [ -n "$paths" ] && helper supports "$paths"; then
                                compatible=true
                                report "Compatible Google Phone APK"
                            else
                                report "Unsupported Google Phone APK"
                            fi
                        fi
                        if "$compatible"; then
                            apply_flags || report "Could not update Google Phone configuration"
                        fi
                    else
                        report "Google Phone must be the default system dialer"
                    fi
                    ;;
                *) report "Waiting for a Japanese SIM" ;;
            esac
        fi
    fi
    # shortcut: polls metadata every 20s; switch to file events if latency matters.
    sleep 20
done
