#!/usr/bin/env bash
# Send one message to the app under test, locating the composer each time.
#
# The composer moves between ~1373 and ~2148 depending on whether a model is
# configured and how tall the transcript is, so a fixed tap coordinate silently
# misses and the run looks like a product failure. This locates the EditText
# from the current hierarchy instead.
#
# Usage: tools/device_send.sh "message text"
set -euo pipefail

MESSAGE="${1:?usage: device_send.sh \"message\"}"
ADB="${ADB:-adb}"
DUMP=/sdcard/linecode-uiautomator.xml

"$ADB" shell uiautomator dump "$DUMP" >/dev/null 2>&1
"$ADB" shell cat "$DUMP" > /tmp/linecode-ui.xml

COORDS=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/linecode-ui.xml").getroot()
fields = []
for node in root.iter("node"):
    if "EditText" not in node.get("class", ""):
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        continue
    x0, y0, x1, y1 = (int(value) for value in match.groups())
    fields.append(((x0 + x1) // 2, (y0 + y1) // 2))
if not fields:
    raise SystemExit("no EditText in the current hierarchy")
# The composer is the lowest editable field on screen.
x, y = max(fields, key=lambda point: point[1])
print(x, y)
PY
)

read -r X Y <<<"$COORDS"
"$ADB" shell input tap "$X" "$Y"
sleep 1.2
"$ADB" shell input text "$MESSAGE"
sleep 0.8
"$ADB" shell input keyevent 66
echo "sent to ($X,$Y): $MESSAGE"
