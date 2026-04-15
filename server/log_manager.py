"""Log manager for RemotePad (Step 1.3).

Provides rotating file logging and an optional window callback handler.
"""

from __future__ import annotations

import logging
import threading
from logging.handlers import RotatingFileHandler
from typing import Callable, Optional

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

LOGGER_NAME = "remotepad"
LOG_FORMAT = "[%(asctime)s] [%(levelname)-8s] [%(name)s] %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
DEFAULT_MAX_BYTES = 1_048_576  # 1 MB
DEFAULT_BACKUP_COUNT = 5


# ---------------------------------------------------------------------------
# Custom handler: forwards formatted records to a callback
# ---------------------------------------------------------------------------

class WindowLogHandler(logging.Handler):
    """Thread-safe logging handler that forwards formatted log lines to a callback."""

    def __init__(self, callback: Callable[[str], None]) -> None:
        super().__init__()
        self._callback = callback
        self._lock_obj = threading.Lock()

    def emit(self, record: logging.LogRecord) -> None:
        try:
            formatted = self.format(record)
            with self._lock_obj:
                self._callback(formatted)
        except Exception:
            self.handleError(record)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def setup_logging(
    log_file: str,
    level: int = logging.INFO,
    window_callback: Optional[Callable[[str], None]] = None,
    max_bytes: int = DEFAULT_MAX_BYTES,
    backup_count: int = DEFAULT_BACKUP_COUNT,
) -> logging.Logger:
    """Configure and return the remotepad logger.

    Args:
        log_file: Path to the log file.
        level: Minimum logging level (default INFO).
        window_callback: Optional callback receiving formatted log strings.
        max_bytes: Max size per log file before rotation.
        backup_count: Number of rotated backup files to keep.

    Returns:
        The configured ``remotepad`` logger.
    """
    logger = logging.getLogger(LOGGER_NAME)
    # Clear any existing handlers to avoid duplicates
    logger.handlers.clear()
    logger.setLevel(level)

    formatter = logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT)

    # Rotating file handler
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=max_bytes,
        backupCount=backup_count,
        encoding="utf-8",
    )
    file_handler.setLevel(level)
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    # Optional window handler
    if window_callback is not None:
        win_handler = WindowLogHandler(window_callback)
        win_handler.setLevel(level)
        win_handler.setFormatter(formatter)
        logger.addHandler(win_handler)

    return logger


def set_debug_mode(enabled: bool) -> None:
    """Toggle debug-level logging on or off at runtime.

    When *enabled* is True the logger and all its handlers are set to DEBUG.
    When False they are restored to INFO.
    """
    logger = logging.getLogger(LOGGER_NAME)
    target_level = logging.DEBUG if enabled else logging.INFO
    logger.setLevel(target_level)
    for handler in logger.handlers:
        handler.setLevel(target_level)
