"""Tests for the message protocol (Step 1.1 - RED phase)."""
import pytest
from server.messages import (
    Message, MouseMove, MouseClick, MouseDoubleClick, MouseScroll,
    KeyPress, KeyCombo, TextInput, AuthRequest, AuthResponse,
    parse_message, serialize_message,
)


class TestParseMouseMove:
    def test_parse_mouse_move(self):
        raw = '{"type": "mouse_move", "dx": 10, "dy": -5}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseMove)
        assert msg.dx == 10
        assert msg.dy == -5

    def test_parse_mouse_move_floats(self):
        raw = '{"type": "mouse_move", "dx": 10.5, "dy": -3.2}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseMove)
        assert msg.dx == 10.5
        assert msg.dy == -3.2


class TestParseMouseClick:
    def test_parse_mouse_click_left(self):
        raw = '{"type": "mouse_click", "button": "left"}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseClick)
        assert msg.button == "left"

    def test_parse_mouse_click_right(self):
        raw = '{"type": "mouse_click", "button": "right"}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseClick)
        assert msg.button == "right"

    def test_parse_mouse_click_middle(self):
        raw = '{"type": "mouse_click", "button": "middle"}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseClick)
        assert msg.button == "middle"

    def test_parse_mouse_click_invalid_button(self):
        raw = '{"type": "mouse_click", "button": "extra"}'
        with pytest.raises(ValueError):
            parse_message(raw)


class TestParseMouseDoubleClick:
    def test_parse_mouse_double_click(self):
        raw = '{"type": "mouse_double_click", "button": "left"}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseDoubleClick)
        assert msg.button == "left"


class TestParseMouseScroll:
    def test_parse_mouse_scroll(self):
        raw = '{"type": "mouse_scroll", "dx": 0, "dy": 3}'
        msg = parse_message(raw)
        assert isinstance(msg, MouseScroll)
        assert msg.dx == 0
        assert msg.dy == 3


class TestParseKeyPress:
    def test_parse_key_press_simple(self):
        raw = '{"type": "key_press", "key": "a", "modifiers": []}'
        msg = parse_message(raw)
        assert isinstance(msg, KeyPress)
        assert msg.key == "a"
        assert msg.modifiers == []

    def test_parse_key_press_with_modifiers(self):
        raw = '{"type": "key_press", "key": "c", "modifiers": ["ctrl"]}'
        msg = parse_message(raw)
        assert isinstance(msg, KeyPress)
        assert msg.key == "c"
        assert msg.modifiers == ["ctrl"]

    def test_parse_key_press_multiple_modifiers(self):
        raw = '{"type": "key_press", "key": "z", "modifiers": ["ctrl", "shift"]}'
        msg = parse_message(raw)
        assert msg.modifiers == ["ctrl", "shift"]


class TestParseKeyCombo:
    def test_parse_key_combo(self):
        raw = '{"type": "key_combo", "keys": ["ctrl", "c"]}'
        msg = parse_message(raw)
        assert isinstance(msg, KeyCombo)
        assert msg.keys == ["ctrl", "c"]


class TestParseTextInput:
    def test_parse_text_input(self):
        raw = '{"type": "text_input", "text": "hello world"}'
        msg = parse_message(raw)
        assert isinstance(msg, TextInput)
        assert msg.text == "hello world"

    def test_parse_text_input_unicode(self):
        raw = '{"type": "text_input", "text": "h\u00e9llo"}'
        msg = parse_message(raw)
        assert msg.text == "h\u00e9llo"


class TestParseAuth:
    def test_parse_auth_request(self):
        raw = '{"type": "auth", "pin": "1234"}'
        msg = parse_message(raw)
        assert isinstance(msg, AuthRequest)
        assert msg.pin == "1234"


class TestParseInvalid:
    def test_parse_invalid_json(self):
        with pytest.raises(ValueError):
            parse_message("not json")

    def test_parse_missing_type(self):
        with pytest.raises(ValueError):
            parse_message('{"dx": 10}')

    def test_parse_unknown_type(self):
        with pytest.raises(ValueError):
            parse_message('{"type": "unknown_thing"}')

    def test_parse_empty_string(self):
        with pytest.raises(ValueError):
            parse_message("")


class TestSerialize:
    def test_serialize_auth_response_success(self):
        msg = AuthResponse(success=True, message="OK")
        raw = serialize_message(msg)
        assert '"type": "auth_response"' in raw or '"type":"auth_response"' in raw
        assert '"success": true' in raw or '"success":true' in raw

    def test_serialize_auth_response_failure(self):
        msg = AuthResponse(success=False, message="Bad PIN")
        raw = serialize_message(msg)
        assert '"success": false' in raw or '"success":false' in raw
        assert "Bad PIN" in raw

    def test_serialize_roundtrip_mouse_move(self):
        original = MouseMove(dx=15, dy=-8)
        raw = serialize_message(original)
        restored = parse_message(raw)
        assert isinstance(restored, MouseMove)
        assert restored.dx == 15
        assert restored.dy == -8

    def test_serialize_roundtrip_key_press(self):
        original = KeyPress(key="a", modifiers=["ctrl", "shift"])
        raw = serialize_message(original)
        restored = parse_message(raw)
        assert isinstance(restored, KeyPress)
        assert restored.key == "a"
        assert restored.modifiers == ["ctrl", "shift"]


class TestAllMessageTypes:
    def test_all_types_parseable(self):
        test_cases = [
            '{"type": "mouse_move", "dx": 0, "dy": 0}',
            '{"type": "mouse_click", "button": "left"}',
            '{"type": "mouse_double_click", "button": "left"}',
            '{"type": "mouse_scroll", "dx": 0, "dy": 0}',
            '{"type": "key_press", "key": "a", "modifiers": []}',
            '{"type": "key_combo", "keys": ["ctrl", "c"]}',
            '{"type": "text_input", "text": "test"}',
            '{"type": "auth", "pin": "0000"}',
        ]
        for raw in test_cases:
            msg = parse_message(raw)
            assert isinstance(msg, Message), f"Failed for: {raw}"
