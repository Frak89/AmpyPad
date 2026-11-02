#!/bin/bash
# Smoke test on an emulator: zoom, shapes, scanner (shared photo → edges → rectify → filter → PDF → email).
set -x
OUT=shots; mkdir -p $OUT
F=".github/smoke/find.py"
shot() { adb exec-out screencap -p > $OUT/$1.png; }
tap() { XY=$(python3 $F "$1" "${2:-0.5}") && adb shell input tap $XY; }
curve() { # cx cy r turns [hold]
  python3 - "$@" > /tmp/c.sh <<'PY'
import math, sys
cx, cy, r, k = map(float, sys.argv[1:5])
pts = [(cx + r * math.cos(t / 30 * math.pi * 2 * k), cy + r * math.sin(t / 30 * math.pi * 2 * k)) for t in range(31)]
print(";".join(["input motionevent DOWN %d %d" % pts[0]] + ["input motionevent MOVE %d %d" % p for p in pts[1:]]))
PY
  adb shell "$(cat /tmp/c.sh)"
}
adb install -r out/AmpyPad.apk
adb logcat -c
adb shell am start -n it.ampy.ampypad/.PadActivity
sleep 8
shot 01_avvio
tap "zoom piu"; sleep 1.5; tap "zoom piu"; sleep 2
shot 02_zoom_150
tap "zoom meno"; sleep 1.5; tap "zoom meno"; sleep 1.5; tap "zoom meno"; sleep 2
shot 03_zoom_80
tap "zoom"; sleep 2
tap "modalita" 0.75; sleep 1.5
tap "forme"; sleep 1.5
shot 04_pannello_forme
tap "forma STELLA"; sleep 1.5
adb shell input swipe 300 900 750 1350 600
sleep 1
shot 05_stella_maniglie
adb shell input swipe 520 1120 620 1500 600
sleep 1
shot 06_stella_spostata
# freehand circle + hold → perfect ellipse
curve 540 800 170 1.0
sleep 1.4
adb shell input motionevent UP 710 800
sleep 1
shot 07_cerchio_perfetto
# scanner via shared photo
adb push .github/smoke/pagina_curva.jpg /data/local/tmp/p.jpg
adb shell run-as it.ampy.ampypad mkdir -p cache/scan
adb shell "cat /data/local/tmp/p.jpg | run-as it.ampy.ampypad sh -c 'cat > cache/scan/p.jpg'"
adb shell am start -n it.ampy.ampypad/.ScanActivity -a android.intent.action.SEND -t image/jpeg --eu android.intent.extra.STREAM content://it.ampy.ampypad.scan/p.jpg
sleep 1
shot 08_rilevamento
sleep 4
shot 09_bordi
tap "RADDRIZZA"; sleep 1
shot 10_raddrizzamento
sleep 5
shot 11_filtro
tap "BIANCO E NERO"; sleep 3
shot 12_bn
tap "DOCUMENTO"; sleep 3
tap "AGGIUNGI PAGINA"; sleep 6
shot 13_lista
tap "SALVA PDF"; sleep 6
shot 14_pdf
tap "INVIA EMAIL"; sleep 2
shot 15_email_dialog
tap "INVIA"; sleep 6
shot 16_app_email
adb shell ls -la /sdcard/Download/AmpyPad/ > $OUT/files.txt 2>&1
adb pull /sdcard/Download/AmpyPad $OUT/pdf
adb logcat -d > $OUT/logcat.txt
grep -E "FATAL|AndroidRuntime: (E|java)|Exception" $OUT/logcat.txt | grep -v uiautomator | head -60 > $OUT/errors.txt
exit 0
