"""Message protocol for RemotePad (Step 1.1).

Defines dataclasses for all message types and provides
parse_message / serialize_message for JSON (de)serialization.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, fields
from typing import Union


# ---------------------------------------------------------------------------
# Base class
# ---------------------------------------------------------------------------

@dataclass
class Message:
    """Base class for all protocol messages."""
    pass


# ---------------------------------------------------------------------------
# Validation bounds
# ---------------------------------------------------------------------------

VALID_BUTTONS = ("left", "right", "middle")

# A single pointer delta / scroll amount is a small screen-space value.
COORD_LIMIT = 100_000.0
# Ctrl+scroll zoom is applied in small steps.
ZOOM_LIMIT = 1_000
# Longest text chunk accepted in one text_input message.
MAX_TEXT_LEN = 4_096
# A key name like "page_down" or a single char; bound to reject junk.
MAX_KEY_NAME_LEN = 32
# No legitimate combo/modifier set is longer than this.
MAX_KEYS = 16


def _check_number(value, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{name} must be a number, got {type(value).__name__}")
    if value != value or abs(value) > COORD_LIMIT:  # NaN or out of range
        raise ValueError(f"{name} out of range: {value}")
    return value


def _check_key_list(values, name: str) -> list[str]:
    if not isinstance(values, list):
        raise ValueError(f"{name} must be a list")
    if len(values) > MAX_KEYS:
        raise ValueError(f"{name} has too many entries ({len(values)})")
    for v in values:
        if not isinstance(v, str) or not (0 < len(v) <= MAX_KEY_NAME_LEN):
            raise ValueError(f"{name} contains an invalid key name")
    return values


# ---------------------------------------------------------------------------
# Mouse messages
# ---------------------------------------------------------------------------

@dataclass
class MouseMove(Message):
    dx: float
    dy: float

    def __post_init__(self) -> None:
        _check_number(self.dx, "dx")
        _check_number(self.dy, "dy")


@dataclass
class MouseClick(Message):
    button: str

    def __post_init__(self) -> None:
        if self.button not in VALID_BUTTONS:
            raise ValueError(
                f"Invalid button '{self.button}', must be one of {VALID_BUTTONS}"
            )


@dataclass
class MouseDoubleClick(Message):
    button: str

    def __post_init__(self) -> None:
        if self.button not in VALID_BUTTONS:
            raise ValueError(
                f"Invalid button '{self.button}', must be one of {VALID_BUTTONS}"
            )


@dataclass
class MouseScroll(Message):
    dx: float
    dy: float

    def __post_init__(self) -> None:
        _check_number(self.dx, "dx")
        _check_number(self.dy, "dy")


# ---------------------------------------------------------------------------
# Keyboard messages
# ---------------------------------------------------------------------------

@dataclass
class KeyPress(Message):
    key: str
    modifiers: list[str]

    def __post_init__(self) -> None:
        if not isinstance(self.key, str) or not (0 < len(self.key) <= MAX_KEY_NAME_LEN):
            raise ValueError("Invalid key name")
        _check_key_list(self.modifiers, "modifiers")


@dataclass
class KeyCombo(Message):
    keys: list[str]

    def __post_init__(self) -> None:
        _check_key_list(self.keys, "keys")
        if not self.keys:
            raise ValueError("keys must not be empty")


@dataclass
class TextInput(Message):
    text: str

    def __post_init__(self) -> None:
        if not isinstance(self.text, str):
            raise ValueError("text must be a string")
        if len(self.text) > MAX_TEXT_LEN:
            raise ValueError(f"text too long ({len(self.text)} > {MAX_TEXT_LEN})")


@dataclass
class Zoom(Message):
    steps: int

    def __post_init__(self) -> None:
        if isinstance(self.steps, bool) or not isinstance(self.steps, int):
            raise ValueError("steps must be an integer")
        if abs(self.steps) > ZOOM_LIMIT:
            raise ValueError(f"steps out of range: {self.steps}")


# ---------------------------------------------------------------------------
# Auth messages
# ---------------------------------------------------------------------------

@dataclass
class AuthRequest(Message):
    pin: str


@dataclass
class AuthResponse(Message):
    success: bool
    message: str


# ---------------------------------------------------------------------------
# Registry: JSON type string <-> dataclass
# ---------------------------------------------------------------------------

# Types a client is allowed to send to the server (inbound). AuthResponse is
# deliberately excluded: it is a server->client message only.
_TYPE_MAP: dict[str, type[Message]] = {
    "mouse_move": MouseMove,
    "mouse_click": MouseClick,
    "mouse_double_click": MouseDoubleClick,
    "mouse_scroll": MouseScroll,
    "key_press": KeyPress,
    "key_combo": KeyCombo,
    "text_input": TextInput,
    "zoom": Zoom,
    "auth": AuthRequest,
}

# Serialization registry: every message class the server may emit, including
# outbound-only ones like AuthResponse.
_CLASS_TO_TYPE: dict[type[Message], str] = {v: k for k, v in _TYPE_MAP.items()}
_CLASS_TO_TYPE[AuthResponse] = "auth_response"


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def parse_message(json_str: str) -> Message:
    """Deserialize a JSON string into the appropriate Message subclass.

    Raises ValueError on invalid JSON, missing 'type', unknown type,
    or missing/invalid fields.
    """
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON: {exc}") from exc

    if not isinstance(data, dict) or "type" not in data:
        raise ValueError("Message must be a JSON object with a 'type' field")

    msg_type = data.pop("type")
    cls = _TYPE_MAP.get(msg_type)
    if cls is None:
        raise ValueError(f"Unknown message type: '{msg_type}'")

    # Extract only the fields declared on the dataclass
    field_names = {f.name for f in fields(cls)}
    kwargs = {k: v for k, v in data.items() if k in field_names}

    try:
        return cls(**kwargs)
    except TypeError as exc:
        raise ValueError(f"Invalid fields for '{msg_type}': {exc}") from exc


def serialize_message(msg: Message) -> str:
    """Serialize a Message instance to a compact JSON string.

    Raises ValueError if the message class is not registered.
    """
    cls = type(msg)
    msg_type = _CLASS_TO_TYPE.get(cls)
    if msg_type is None:
        raise ValueError(f"Cannot serialize unregistered message class: {cls.__name__}")

    data: dict = {"type": msg_type}
    for f in fields(cls):
        data[f.name] = getattr(msg, f.name)

    return json.dumps(data, separators=(",", ":"), ensure_ascii=False)
