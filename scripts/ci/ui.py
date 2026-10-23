#!/usr/bin/env python3
"""Drives the app on a device through adb and the accessibility tree, with nothing installed beside it.

    ui.py wait <text> [seconds]        until a node shows <text> (its text or its description)
    ui.py tap <text> [seconds]         taps the node that shows <text>, scrolling down to it
    ui.py tab <text> [seconds]         taps <text> in the floating bar at the bottom: a tab, or a step's button
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
# A fifth of the screen each, so this many go well past the end of a long settings page.
SCROLL_STEPS = 8
# No adb call may hang a step: a device that stops answering or a screen that never settles fails it instead.
ADB_TIMEOUT = 60


def adb(*args, capture=True):
    return subprocess.run(ADB + list(args), check=True, capture_output=capture, text=True, timeout=ADB_TIMEOUT).stdout


def nodes():
    # The dump fails now and then while the screen animates; the next try usually works.
    for _ in range(5):
        try:
            adb("shell", "uiautomator", "dump", DUMP)
            tree = ElementTree.fromstring(adb("shell", "cat", DUMP))
            return list(tree.iter("node"))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ElementTree.ParseError):
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
    """In the middle of the screen: the floating top and bottom lenses of blesk lie over the content
    near the edges, and a tap there would land on them instead."""
    left, top, right, bottom = bounds(node)
    middle = (top + bottom) // 2
    return right > left and bottom > top and height * 0.15 <= middle <= height * 0.78


def dismiss_not_responding(all_nodes):
    """A slow emulator now and then shows «... isn't responding» over everything; waiting is the answer."""
    stuck = any(re.search(r"isn.t responding|не отвечает", node.get("text") or "") for node in all_nodes)
    wait = [node for node in all_nodes if shows(node, "Wait") or shows(node, "Подождать")]
    if stuck and wait:
        tap_node(wait[0])
        return True
    return False


def in_bottom_bar(node, height):
    _, top, _, bottom = bounds(node)
    return (top + bottom) // 2 > height * 0.78


def find(text, seconds, scroll=False, kind=lambda node: True):
    """The node of [kind] showing [text]. With [scroll] the screen moves in short slow steps, first
    down the page and then back up, since a long swipe flings past what it looks for."""
    deadline = time.monotonic() + seconds
    width, height = screen_size()
    steps = 0
    while True:
        all_nodes = nodes()
        if dismiss_not_responding(all_nodes):
            time.sleep(1)
            continue
        found = [node for node in all_nodes if shows(node, text) and kind(node)]
        # Without scrolling there is nothing to wait for but the node itself, wherever it is.
        visible = [node for node in found if on_screen(node, height) or not scroll]
        if visible:
            return visible[0], all_nodes
        if time.monotonic() > deadline:
            raise SystemExit(f"no «{text}» on the screen after {seconds} s; it shows: {screen_texts(all_nodes)}")
        if scroll:
            down_the_page = steps < SCROLL_STEPS
            start, end = (height * 3 // 5, height * 2 // 5) if down_the_page else (height * 2 // 5, height * 3 // 5)
            adb("shell", "input", "swipe", str(width // 2), str(start), str(width // 2), str(end), "900")
            steps = (steps + 1) % (SCROLL_STEPS * 2)
        time.sleep(1)


def tap_node(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def is_field(node):
    return node.get("class") == "android.widget.EditText" or node.get("focusable") == "true" and node.get("long-clickable") == "true"


def describe_fields(all_nodes):
    return [(node.get("class"), node.get("text"), node.get("content-desc"), node.get("bounds")) for node in all_nodes if is_field(node)]


def field_for(label, all_nodes):
    """The field [label] describes, or else the field nearest below the text [label]."""
    wanted = label.get("text") or label.get("content-desc") or ""
    described = [node for node in all_nodes if is_field(node) and shows(node, wanted)]
    if described:
        return described[0]
    _, top, _, bottom = bounds(label)
    below = [node for node in all_nodes if is_field(node) and bounds(node)[1] >= top]
    if not below:
        raise SystemExit(f"no text field near «{wanted}»; fields on the screen: {describe_fields(all_nodes)}")
    return min(below, key=lambda node: bounds(node)[1] - bottom)


def bring_to_middle(node, width, height):
    """A slow swipe that moves [node] to the middle of the screen, where nothing floats over it."""
    _, top, _, bottom = bounds(node)
    shift = (top + bottom) // 2 - height // 2
    start, end = height // 2 + shift // 2, height // 2 - shift // 2
    adb("shell", "input", "swipe", str(width // 2), str(start), str(width // 2), str(end), "900")


def type_into(label, value, seconds=60):
    """Types into the text field under [label]: blesk's fields carry a label above them. The text goes
    in only once that field has the focus, since `input text` types wherever the focus happens to be."""
    deadline = time.monotonic() + seconds
    width, height = screen_size()
    while True:
        found, all_nodes = find(label, max(deadline - time.monotonic(), 1), scroll=True)
        field = field_for(found, all_nodes)
        if field.get("focused") == "true":
            break
        if time.monotonic() > deadline:
            raise SystemExit(f"the field under «{label}» never took the focus; fields: {describe_fields(all_nodes)}")
        if on_screen(field, height):
            tap_node(field)
        else:
            bring_to_middle(field, width, height)
        time.sleep(1)
    # `input text` takes the text as one shell word: quote it for the device's shell.
    adb("shell", "input", "text", "'" + value.replace("'", "'\\''") + "'")


def main(argv):
    command, *args = argv or ["help"]
    if command == "wait":
        find(args[0], float(args[1]) if len(args) > 1 else 30)
    elif command == "tap":
        node, _ = find(args[0], float(args[1]) if len(args) > 1 else 60, scroll=True)
        tap_node(node)
    elif command == "tab":
        _, height = screen_size()
        node, _ = find(args[0], float(args[1]) if len(args) > 1 else 60, kind=lambda node: in_bottom_bar(node, height))
        tap_node(node)
    elif command == "type":
        type_into(args[0], args[1])
    elif command == "screenshot":
        with open(args[0], "wb") as out:
            out.write(subprocess.run(ADB + ["exec-out", "screencap", "-p"], check=True, capture_output=True, timeout=ADB_TIMEOUT).stdout)
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except subprocess.TimeoutExpired as late:
        sys.exit(f"adb did not answer in {ADB_TIMEOUT} s: {' '.join(late.cmd)}")
