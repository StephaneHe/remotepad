"""Input controller for RemotePad (Step 1.2).

Translates protocol messages into real mouse/keyboard actions
on Windows via pynput.
"""

from __future__ import annotations

from pynput.mouse import Button, Controller as PynputMouseController
from pynput.keyboard import Key, KeyCode, Controller as PynputKeyboardController

from server.messages import (
    Message, MouseMove, MouseClick, MouseDoubleClick, MouseScroll,
    KeyPress, KeyCombo, TextInput,
)

# ---------------------------------------------------------------------------
# Special key mapping: string name -> pynput Key constant
# ---------------------------------------------------------------------------

SPECIAL_KEYS: dict[str, Key] = {
    "ctrl": Key.ctrl,
    "alt": Key.alt,
    "shift": Key.shift,
    "tab": Key.tab,
    "Tab": Key.tab,
    "enter": Key.enter,
    "Return": Key.enter,
    "backspace": Key.backspace,
    "BackSpace": Key.backspace,
    "esc": Key.esc,
    "Escape": Key.esc,
    "win": Key.cmd,
    "super": Key.cmd,
    "space": Key.space,
    "delete": Key.delete,
    "Delete": Key.delete,
    "home": Key.home,
    "end": Key.end,
    "page_up": Key.page_up,
    "page_down": Key.page_down,
    "up": Key.up,
    "Up": Key.up,
    "down": Key.down,
    "Down": Key.down,
    "left": Key.left,
    "Left": Key.left,
    "right": Key.right,
    "Right": Key.right,
    "caps_lock": Key.caps_lock,
    "f1": Key.f1,
    "f2": Key.f2,
    "f3": Key.f3,
    "f4": Key.f4,
    "f5": Key.f5,
    "f6": Key.f6,
    "f7": Key.f7,
    "f8": Key.f8,
    "f9": Key.f9,
    "f10": Key.f10,
    "f11": Key.f11,
    "f12": Key.f12,
    "volume_up": Key.media_volume_up,
    "volume_down": Key.media_volume_down,
    "volume_mute": Key.media_volume_mute,
}

# Mouse button mapping: string name -> pynput Button
BUTTON_MAP: dict[str, Button] = {
    "left": Button.left,
    "right": Button.right,
    "middle": Button.middle,
}


# ---------------------------------------------------------------------------
# InputController
# ---------------------------------------------------------------------------

class InputController:
    """Dispatches protocol messages to pynput mouse/keyboard controllers."""

    def __init__(self) -> None:
        self._mouse = PynputMouseController()
        self._keyboard = PynputKeyboardController()

    # -- public API ---------------------------------------------------------

    def handle(self, message: Message) -> None:
        """Dispatch a Message to the appropriate handler."""
        handler = self._DISPATCH.get(type(message))
        if handler is None:
            raise ValueError(f"Unsupported message type: {type(message).__name__}")
        handler(self, message)

    # -- private handlers ---------------------------------------------------

    def _handle_mouse_move(self, msg: MouseMove) -> None:
        self._mouse.move(msg.dx, msg.dy)

    def _handle_mouse_click(self, msg: MouseClick) -> None:
        button = BUTTON_MAP[msg.button]
        self._mouse.click(button, 1)

    def _handle_mouse_double_click(self, msg: MouseDoubleClick) -> None:
        button = BUTTON_MAP[msg.button]
        self._mouse.click(button, 2)

    def _handle_mouse_scroll(self, msg: MouseScroll) -> None:
        self._mouse.scroll(msg.dx, msg.dy)

    def _handle_key_press(self, msg: KeyPress) -> None:
        resolved_key = _resolve_key(msg.key)
        mod_keys = [_resolve_key(m) for m in msg.modifiers]

        # Press modifiers first
        for mk in mod_keys:
            self._keyboard.press(mk)
        # Press and release the main key
        self._keyboard.press(resolved_key)
        self._keyboard.release(resolved_key)
        # Release modifiers in reverse order
        for mk in reversed(mod_keys):
            self._keyboard.release(mk)

    def _handle_key_combo(self, msg: KeyCombo) -> None:
        resolved = [_resolve_key(k) for k in msg.keys]
        # Press all keys in order
        for k in resolved:
            self._keyboard.press(k)
        # Release in reverse order
        for k in reversed(resolved):
            self._keyboard.release(k)

    def _handle_text_input(self, msg: TextInput) -> None:
        self._keyboard.type(msg.text)

    # -- dispatch table -----------------------------------------------------

    _DISPATCH: dict[type, callable] = {
        MouseMove: _handle_mouse_move,
        MouseClick: _handle_mouse_click,
        MouseDoubleClick: _handle_mouse_double_click,
        MouseScroll: _handle_mouse_scroll,
        KeyPress: _handle_key_press,
        KeyCombo: _handle_key_combo,
        TextInput: _handle_text_input,
    }


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _resolve_key(name: str) -> Key | KeyCode:
    """Convert a key name string to a pynput Key or KeyCode."""
    special = SPECIAL_KEYS.get(name)
    if special is not None:
        return special
    # Single character -> KeyCode
    if len(name) == 1:
        return KeyCode.from_char(name)
    raise ValueError(f"Unknown key name: '{name}'")
