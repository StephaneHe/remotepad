"""Tests for the input controller (Step 1.2 - RED phase).

All pynput interactions are mocked so no real mouse/keyboard
actions are triggered during tests.
"""
import pytest
from unittest.mock import MagicMock, call, patch

from server.messages import (
    MouseMove, MouseClick, MouseDoubleClick, MouseScroll,
    KeyPress, KeyCombo, TextInput,
)
from server.input_controller import InputController


@pytest.fixture
def controller():
    """Create an InputController with mocked pynput backends."""
    with patch("server.input_controller.PynputMouseController") as mock_mouse_cls, \
         patch("server.input_controller.PynputKeyboardController") as mock_kb_cls:
        mock_mouse = MagicMock()
        mock_kb = MagicMock()
        mock_mouse_cls.return_value = mock_mouse
        mock_kb_cls.return_value = mock_kb
        ctrl = InputController()
        ctrl._mouse = mock_mouse
        ctrl._keyboard = mock_kb
        yield ctrl


class TestMouseMove:
    def test_mouse_move_calls_pynput(self, controller):
        controller.handle(MouseMove(dx=10, dy=-5))
        controller._mouse.move.assert_called_once_with(10, -5)

    def test_mouse_move_zero(self, controller):
        controller.handle(MouseMove(dx=0, dy=0))
        controller._mouse.move.assert_called_once_with(0, 0)


class TestMouseClick:
    def test_mouse_click_left(self, controller):
        controller.handle(MouseClick(button="left"))
        controller._mouse.click.assert_called_once()
        args = controller._mouse.click.call_args
        assert args[0][1] == 1  # count=1

    def test_mouse_click_right(self, controller):
        controller.handle(MouseClick(button="right"))
        controller._mouse.click.assert_called_once()

    def test_mouse_click_middle(self, controller):
        controller.handle(MouseClick(button="middle"))
        controller._mouse.click.assert_called_once()


class TestMouseDoubleClick:
    def test_mouse_double_click(self, controller):
        controller.handle(MouseDoubleClick(button="left"))
        controller._mouse.click.assert_called_once()
        args = controller._mouse.click.call_args
        assert args[0][1] == 2  # count=2


class TestMouseScroll:
    def test_mouse_scroll(self, controller):
        controller.handle(MouseScroll(dx=0, dy=3))
        controller._mouse.scroll.assert_called_once_with(0, 3)

    def test_mouse_scroll_horizontal(self, controller):
        controller.handle(MouseScroll(dx=-2, dy=0))
        controller._mouse.scroll.assert_called_once_with(-2, 0)


class TestKeyPressSimple:
    def test_key_press_simple(self, controller):
        controller.handle(KeyPress(key="a", modifiers=[]))
        controller._keyboard.press.assert_called()
        controller._keyboard.release.assert_called()

    def test_key_press_with_modifier(self, controller):
        controller.handle(KeyPress(key="a", modifiers=["ctrl"]))
        # ctrl should be pressed before 'a' and released after 'a'
        press_calls = controller._keyboard.press.call_args_list
        release_calls = controller._keyboard.release.call_args_list
        assert len(press_calls) == 2   # ctrl, a
        assert len(release_calls) == 2  # a, ctrl

    def test_key_press_multiple_modifiers(self, controller):
        controller.handle(KeyPress(key="z", modifiers=["ctrl", "shift"]))
        press_calls = controller._keyboard.press.call_args_list
        release_calls = controller._keyboard.release.call_args_list
        assert len(press_calls) == 3   # ctrl, shift, z
        assert len(release_calls) == 3  # z, shift, ctrl


class TestKeyCombo:
    def test_key_combo(self, controller):
        controller.handle(KeyCombo(keys=["ctrl", "c"]))
        press_calls = controller._keyboard.press.call_args_list
        release_calls = controller._keyboard.release.call_args_list
        # Press order: ctrl, c  |  Release order: c, ctrl
        assert len(press_calls) == 2
        assert len(release_calls) == 2


class TestTextInput:
    def test_text_input(self, controller):
        controller.handle(TextInput(text="hello"))
        controller._keyboard.type.assert_called_once_with("hello")

    def test_text_input_unicode(self, controller):
        controller.handle(TextInput(text="héllo"))
        controller._keyboard.type.assert_called_once_with("héllo")


class TestSpecialKeyMapping:
    def test_special_key_mapping(self, controller):
        """All required special keys should be in the mapping."""
        from server.input_controller import SPECIAL_KEYS
        required = ["ctrl", "alt", "shift", "tab", "enter", "backspace", "esc", "win"]
        for name in required:
            assert name in SPECIAL_KEYS, f"Missing special key mapping: {name}"

    def test_return_alias(self, controller):
        from server.input_controller import SPECIAL_KEYS
        # "Return" should also be mapped
        assert "Return" in SPECIAL_KEYS or "return" in SPECIAL_KEYS

    def test_escape_alias(self, controller):
        from server.input_controller import SPECIAL_KEYS
        assert "Escape" in SPECIAL_KEYS or "escape" in SPECIAL_KEYS
