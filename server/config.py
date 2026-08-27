"""Configuration manager for RemotePad (Step 1.6).

Loads, validates, and persists JSON configuration.
"""

from __future__ import annotations

import json
import logging
import os

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 9876
DEFAULT_LOG_LEVEL = "INFO"
DEFAULT_AUTO_START = False

PORT_MIN = 1024
PORT_MAX = 65535


class Config:
    """JSON-backed configuration with defaults and validation."""

    def __init__(self, config_path: str) -> None:
        self._path = config_path
        self._host: str = DEFAULT_HOST
        self._port: int = DEFAULT_PORT
        self._log_level: str = DEFAULT_LOG_LEVEL
        self._auto_start: bool = DEFAULT_AUTO_START

    # -- Properties ---------------------------------------------------------

    @property
    def host(self) -> str:
        return self._host

    @host.setter
    def host(self, value: str) -> None:
        if not isinstance(value, str) or not value:
            raise ValueError(f"Host must be a non-empty string, got {value!r}")
        self._host = value

    @property
    def port(self) -> int:
        return self._port

    @port.setter
    def port(self, value: int) -> None:
        if value < PORT_MIN or value > PORT_MAX:
            raise ValueError(
                f"Port must be between {PORT_MIN} and {PORT_MAX}, got {value}"
            )
        self._port = value

    @property
    def log_level(self) -> str:
        return self._log_level

    @log_level.setter
    def log_level(self, value: str) -> None:
        self._log_level = value

    @property
    def auto_start(self) -> bool:
        return self._auto_start

    @auto_start.setter
    def auto_start(self, value: bool) -> None:
        self._auto_start = value

    # -- Persistence --------------------------------------------------------

    def load(self) -> None:
        """Load configuration from JSON file, applying defaults for missing keys.

        Values are applied through the validating setters; any invalid value
        falls back to its default instead of silently corrupting runtime state.
        """
        if os.path.exists(self._path):
            with open(self._path, encoding="utf-8") as f:
                data = json.load(f)
            self._apply("host", data.get("host", DEFAULT_HOST), DEFAULT_HOST)
            self._apply("port", data.get("port", DEFAULT_PORT), DEFAULT_PORT)
            self.log_level = data.get("log_level", DEFAULT_LOG_LEVEL)
            self.auto_start = bool(data.get("auto_start", DEFAULT_AUTO_START))
            logger.info("Configuration loaded from %s", self._path)
        else:
            logger.info("No config file found, using defaults")
            self.save()

    def _apply(self, attr: str, value, default) -> None:
        """Set *attr* via its validating setter, falling back to *default*."""
        try:
            setattr(self, attr, value)
        except (ValueError, TypeError):
            logger.warning(
                "Invalid %s %r in config, falling back to default %r",
                attr, value, default,
            )
            setattr(self, attr, default)

    def save(self) -> None:
        """Persist current configuration to JSON file."""
        data = {
            "host": self._host,
            "port": self._port,
            "log_level": self._log_level,
            "auto_start": self._auto_start,
        }
        with open(self._path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        logger.info("Configuration saved to %s", self._path)
