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
# Mouse messages
# ---------------------------------------------------------------------------

VALID_BUTTONS = ("left", "right", "middle")


@dataclass
class MouseMove(Message):
    dx: float
    dy: float


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


# ---------------------------------------------------------------------------
# Keyboard messages
# ---------------------------------------------------------------------------

@dataclass
class KeyPress(Message):
    key: str
    modifiers: list[str]


@dataclass
class KeyCombo(Message):
    keys: list[str]


@dataclass
class TextInput(Message):
    text: str


@dataclass
class Zoom(Message):
    steps: int


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
    "auth_response": AuthResponse,
}

_CLASS_TO_TYPE: dict[type[Message], str] = {v: k for k, v in _TYPE_MAP.items()}


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
