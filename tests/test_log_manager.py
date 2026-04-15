"""Tests for the log manager (Step 1.3 - RED phase)."""

import logging
import os
import tempfile
import threading
import re

import pytest

from server.log_manager import (
    setup_logging,
    set_debug_mode,
    WindowLogHandler,
    LOG_FORMAT,
)


@pytest.fixture(autouse=True)
def reset_logger():
    """Ensure the remotepad logger is clean before each test."""
    logger = logging.getLogger("remotepad")
    logger.handlers.clear()
    logger.setLevel(logging.DEBUG)
    yield
    logger.handlers.clear()


class TestLogToFile:
    def test_log_to_file(self, tmp_path):
        log_file = tmp_path / "test.log"
        logger = setup_logging(log_file=str(log_file), level=logging.INFO)
        logger.info("Server started")
        # Flush handlers
        for h in logger.handlers:
            h.flush()
        content = log_file.read_text(encoding="utf-8")
        assert "Server started" in content

    def test_log_creates_file(self, tmp_path):
        log_file = tmp_path / "new.log"
        assert not log_file.exists()
        logger = setup_logging(log_file=str(log_file), level=logging.INFO)
        logger.info("init")
        for h in logger.handlers:
            h.flush()
        assert log_file.exists()


class TestLogFormat:
    def test_log_format(self, tmp_path):
        log_file = tmp_path / "fmt.log"
        logger = setup_logging(log_file=str(log_file), level=logging.INFO)
        logger.info("test message")
        for h in logger.handlers:
            h.flush()
        content = log_file.read_text(encoding="utf-8")
        # Expected pattern: [YYYY-MM-DD HH:MM:SS] [LEVEL] [MODULE] message
        pattern = r"\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] \[INFO\s*\] \[\w+\] test message"
        assert re.search(pattern, content), f"Format mismatch in: {content!r}"


class TestLogRotation:
    def test_log_rotation(self, tmp_path):
        log_file = tmp_path / "rotate.log"
        # Use a very small maxBytes to force rotation
        logger = setup_logging(
            log_file=str(log_file), level=logging.INFO, max_bytes=200, backup_count=2
        )
        for i in range(100):
            logger.info(f"Line {i:04d} padding to force rotation with extra text here")
        for h in logger.handlers:
            h.flush()
        # Should have created backup files
        rotated = list(tmp_path.glob("rotate.log*"))
        assert len(rotated) > 1, f"Expected rotation, got: {[f.name for f in rotated]}"


class TestDebugToggle:
    def test_debug_toggle_off(self, tmp_path):
        log_file = tmp_path / "debug_off.log"
        logger = setup_logging(log_file=str(log_file), level=logging.INFO)
        set_debug_mode(False)
        logger.debug("mouse_move dx=12 dy=-3")
        for h in logger.handlers:
            h.flush()
        content = log_file.read_text(encoding="utf-8")
        assert "mouse_move" not in content

    def test_debug_toggle_on(self, tmp_path):
        log_file = tmp_path / "debug_on.log"
        logger = setup_logging(log_file=str(log_file), level=logging.INFO)
        set_debug_mode(True)
        logger.debug("mouse_move dx=12 dy=-3")
        for h in logger.handlers:
            h.flush()
        content = log_file.read_text(encoding="utf-8")
        assert "mouse_move" in content


class TestWindowHandler:
    def test_window_handler(self, tmp_path):
        log_file = tmp_path / "win.log"
        received = []
        logger = setup_logging(
            log_file=str(log_file),
            level=logging.INFO,
            window_callback=lambda record: received.append(record),
        )
        logger.info("Hello window")
        assert len(received) == 1
        assert "Hello window" in received[0]

    def test_window_handler_thread_safe(self, tmp_path):
        log_file = tmp_path / "thread.log"
        received = []
        logger = setup_logging(
            log_file=str(log_file),
            level=logging.INFO,
            window_callback=lambda record: received.append(record),
        )
        threads = []
        for i in range(10):
            t = threading.Thread(target=logger.info, args=(f"msg-{i}",))
            threads.append(t)
            t.start()
        for t in threads:
            t.join()
        assert len(received) == 10
        # All messages should be present (no corruption)
        messages = " ".join(received)
        for i in range(10):
            assert f"msg-{i}" in messages
