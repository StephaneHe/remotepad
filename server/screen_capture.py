"""Cursor region screen capture for RemotePad.

Captures a small JPEG snapshot of the area around the mouse cursor
and returns raw bytes suitable for binary WebSocket transmission.
"""

from __future__ import annotations

import ctypes
import io

from PIL import ImageGrab, ImageDraw


class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]


def capture_cursor_region(
    width: int = 480,
    height: int = 160,
    quality: int = 50,
) -> bytes:
    """Capture a JPEG of the screen region centered on the mouse cursor."""
    pt = POINT()
    ctypes.windll.user32.GetCursorPos(ctypes.byref(pt))

    left = pt.x - width // 2
    top = pt.y - height // 2
    right = left + width
    bottom = top + height

    img = ImageGrab.grab(bbox=(left, top, right, bottom), all_screens=True)

    # Draw cursor crosshair at centre
    draw = ImageDraw.Draw(img)
    cx, cy = width // 2, height // 2
    # Black outline for contrast
    draw.line([(cx - 8, cy), (cx + 8, cy)], fill=(0, 0, 0), width=3)
    draw.line([(cx, cy - 8), (cx, cy + 8)], fill=(0, 0, 0), width=3)
    # White inner line
    draw.line([(cx - 7, cy), (cx + 7, cy)], fill=(255, 255, 255), width=1)
    draw.line([(cx, cy - 7), (cx, cy + 7)], fill=(255, 255, 255), width=1)

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality)
    return buf.getvalue()
