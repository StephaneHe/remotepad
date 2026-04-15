"""Transport abstraction for RemotePad (Step 3.1).

Defines a common interface for WiFi and Bluetooth server transports.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Callable, Optional


class Transport(ABC):
    """Abstract base for server-side transports (WiFi, Bluetooth)."""

    @abstractmethod
    def start(self) -> None:
        """Start listening for client connections."""

    @abstractmethod
    def stop(self) -> None:
        """Stop the transport and close all connections."""

    @abstractmethod
    def send(self, client_id: str, message: str) -> None:
        """Send a message to a specific connected client."""

    @property
    @abstractmethod
    def is_running(self) -> bool:
        """Whether the transport is currently running."""
