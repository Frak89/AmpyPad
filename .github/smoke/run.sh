#!/bin/bash
# Smoke test on an emulator: launch, write, draw, open panels, export. Screenshots in shots/.
set -x
OUT=shots; mkdir -p $OUT
F=".github/smoke/find.py"
shot() { adb exec-out screencap -p > $OUT/$1.png; }
tap() { XY=$(python3 $F "$1" "${2:-0.5}") && adb shell input tap $XY; }
curve() { # cx cy r turns
  python3 - "$@" > /tmp/c.sh <<'PY'
import math, sys
cx, cy, r, k = map(float, sys.argv[1:5])
pts = [(cx + r * math.cos(t / 30 * math.pi * 2 * k) * (0.4 + 0.6 * t / 30), cy + r * math.sin(t / 30 * math.pi * 2 * k) * (0.4 + 0.6 * t / 30)) for t in range(31)]
cmds = ["input motionevent DOWN %d %d" % pts[0]] + ["input motionevent MOVE %d %d" % p for p in pts[1:]] + ["input motionevent UP %d %d" % pts[-1]]
print(";".join(cmds))
PY
  adb shell "$(cat /tmp/c.sh)"
}
adb install -r out/AmpyPad.apk
adb logcat -c
adb shell am start -n it.ampy.ampypad/.PadActivity
sleep 8
stats() { adb shell uiautomator dump /sdcard/u.xml >/dev/null; adb shell cat /sdcard/u.xml | grep -o 'text="[^"]*TRATTI"' >> $OUT/stats.txt; }
tap "modalita" 0.75
sleep 2
curve 540 1300 220 1.5
sleep 2.5
stats
tap "PENNA"
sleep 1.5
tap "ACQUERELLO"
sleep 2
curve 420 1000 170 1
sleep 2.5
stats
shot 01_acquerello
tap "ACQUERELLO"
sleep 1.5
tap "NEON"
sleep 1.5
tap "✣"
sleep 1.5
tap "CALEIDOSCOPIO"
sleep 1.5
adb shell input keyevent KEYCODE_BACK
sleep 1
curve 600 1200 120 0.7
sleep 0.5
shot 02_neon_caleido
sleep 2.5
stats
shot 03_dopo
tap "⇩"
sleep 1.5
tap "PNG · FOGLIO"
sleep 6
shot 04_png
adb shell ls -la /sdcard/Pictures/AmpyPad/ > $OUT/files.txt 2>&1
adb pull /sdcard/Pictures/AmpyPad $OUT/png
adb logcat -d > $OUT/logcat.txt
exit 0
