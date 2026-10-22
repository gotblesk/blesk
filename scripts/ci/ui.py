#!/usr/bin/env python3
"""Drives the app on a device through adb and the accessibility tree, with nothing installed beside it.

    ui.py wait <text> [seconds]        until a node shows <text> (its text or its description)
    ui.py tap <text> [seconds]         taps the node that shows <text>, scrolling down to it
    ui.py type <label> <value>         types <value> into the text field labelled <label>
    ui.py screenshot <file.png>        saves the screen

A node matches when its text or content description equals <text>, ignoring case and spaces at the ends.
The device should have no on-screen keyboard (smoke.sh turns it off): `input text` needs none, and a
keyboard would cover the buttons below the fields.
Each failure prints the texts that were on the screen, which is what a broken step needs to be read.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree

ADB = ["adb"]
DUMP = "/sdcard/blesk-ui.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, capture=True):
    return subprocess.run(ADB + list(args), check=True, capture_output=capture, text=True).stdout


def nodes():
    # The dump fails now and then while the screen animates; the next try usually works.
    for _ in range(5):
        try:
            adb("shell", "uiautomator", "dump", DUMP)
            tree = ElementTree.fromstring(adb("shell", "cat", DUMP))
            return list(tree.iter("node"))
        except (subprocess.CalledProcessError, ElementTree.ParseError):
            time.sleep(1)
    raise SystemExit("could not read the screen")


def bounds(node):
    left, top, right, bottom = map(int, BOUNDS.match(node.get("bounds")).groups())
    return left, top, right, bottom


def shows(node, text):
    wanted = text.strip().casefold()
    return any((node.get(key) or "").strip().casefold() == wanted for key in ("text", "content-desc"))


def screen_texts(all_nodes):
    return sorted({value for node in all_nodes for value in (node.get("text"), node.get("content-desc")) if value})


def screen_size():
    width, height = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups()
    return int(width), int(height)


def on_screen(node, height):
    left, top, right, bottom = bounds(node)
    return right > left and bottom > top and top >= 0 and bottom <= height


def find(text, seconds, scroll=False, kind=lambda node: True):
    """The node of [kind] showing [text]; with [scroll] the screen is swiped up until it is in full view."""
    deadline = time.monotonic() + seconds
    width, height = screen_size()
    while True:
        all_nodes = nodes()
        found = [node for node in all_nodes if shows(node, text) and kind(node)]
        visible = [node for node in found if on_screen(node, height)]
        if visible:
            return visible[0], all_nodes
        if time.monotonic() > deadline:
            raise SystemExit(f"no «{text}» on the screen after {seconds} s; it shows: {screen_texts(all_nodes)}")
        if scroll:
            middle = str(width // 2)
            adb("shell", "input", "swipe", middle, str(height * 3 // 4), middle, str(height // 3), "600")
        time.sleep(1)


def tap_node(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def is_field(node):
    return node.get("class") == "android.widget.EditText"


def type_into(label, value):
    """Types into the text field described by [label]: blesk's fields carry their label as description."""
    field, _ = find(label, 20, scroll=True, kind=is_field)
    tap_node(field)
    time.sleep(0.5)
    # `input text` takes the text as one shell word: quote it for the device's shell.
    adb("shell", "input", "text", "'" + value.replace("'", "'\\''") + "'")


def main(argv):
    command, *args = argv or ["help"]
    if command == "wait":
        find(args[0], float(args[1]) if len(args) > 1 else 30)
    elif command == "tap":
        node, _ = find(args[0], float(args[1]) if len(args) > 1 else 30, scroll=True)
        tap_node(node)
    elif command == "type":
        type_into(args[0], args[1])
    elif command == "screenshot":
        with open(args[0], "wb") as out:
            out.write(subprocess.run(ADB + ["exec-out", "screencap", "-p"], check=True, capture_output=True).stdout)
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
