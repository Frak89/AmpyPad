#!/usr/bin/env python3
"""Print 'x y' of the first UI node whose text or content-desc contains argv[1].
Optional argv[2] = horizontal fraction inside the node (0..1)."""
import re, subprocess, sys
q = sys.argv[1]
fx = float(sys.argv[2]) if len(sys.argv) > 2 else 0.5
for _ in range(4):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/u.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/u.xml"], capture_output=True, text=True).stdout
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
        if (t and q in t.group(1)) or (d and q in d.group(1)):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            x1, y1, x2, y2 = map(int, b.groups())
            print(int(x1 + (x2 - x1) * fx), (y1 + y2) // 2)
            sys.exit(0)
print("NOT FOUND: " + q, file=sys.stderr)
sys.exit(1)
