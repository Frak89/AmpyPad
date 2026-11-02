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
adb shell settings put global window_animation_scale 1
adb logcat -c
adb shell am start -n it.ampy.ampypad/.PadActivity
sleep 8
shot 01_avvio
tap "Scrivi, disegna" 0.9
sleep 1
adb shell input keyevent KEYCODE_MOVE_END
adb shell input text "%sCiao%sAmpyPad"
shot 02_piuma_scrive
sleep 1.3
shot 03_piuma_si_posa
sleep 1.5
shot 04_dopo
adb shell input keyevent KEYCODE_ENTER
adb shell input text "-%sprimo%spunto"
sleep 2
shot 05_elenco
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "modalita" 0.75
sleep 1.5
shot 06_pennello
curve 540 1300 220 1.5
sleep 0.4
shot 07_disegno_piuma
sleep 2.5
tap "PENNA"
sleep 1.5
shot 08_pannello_pennelli
tap "ACQUERELLO"
sleep 1
curve 400 1000 160 1
tap "colore pennello"
sleep 1.5
shot 09_tavolozza
tap "APPLICA"
sleep 1
tap "▤"
sleep 1.5
shot 10_stile_foglio
tap "stile LAVAGNA"
sleep 0.35
shot 11_cambio_stile
sleep 1.5
shot 12_lavagna
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "✒"
sleep 1.5
shot 13_piuma_pannello
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "FOGLIO" 0.5
sleep 0.3
shot 14_transizione
sleep 2
shot 15_nuovo_foglio
tap "☰"
sleep 1.2
shot 16_quaderni
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "⇩"
sleep 1.2
shot 17_esporta
tap "PDF · FOGLIO"
sleep 5
shot 18_pdf
adb shell ls -la /sdcard/Download/AmpyPad/ > $OUT/files.txt 2>&1
adb shell ls -la /sdcard/Pictures/AmpyPad/ >> $OUT/files.txt 2>&1
adb logcat -d > $OUT/logcat.txt
grep -E "FATAL|AndroidRuntime|ampypad" $OUT/logcat.txt | head -80 > $OUT/errors.txt
exit 0
