"""System tray application for RemotePad (Step 1.6).

Provides a Windows system tray icon with a context menu
to display the PIN, toggle debug mode, and quit the server.
"""

from __future__ import annotations

import logging
import socket
import threading

import pystray
from PIL import Image, ImageDraw

from server import __version__
from server.auth_manager import AuthManager
from server.config import Config
from server.log_manager import set_debug_mode

logger = logging.getLogger(__name__)


def _get_local_ip() -> str:
    """Return the local LAN IP address."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def _create_icon_image(color: str = "green", size: int = 64) -> Image.Image:
    """Create a simple square icon image."""
    img = Image.new("RGB", (size, size), color)
    draw = ImageDraw.Draw(img)
    # Draw a small "R" in white for RemotePad
    draw.text((size // 4, size // 8), "R", fill="white")
    return img


class TrayApp:
    """Windows system tray application for RemotePad."""

    def __init__(
        self,
        server,
        auth_manager: AuthManager,
        config: Config,
        stop_event: threading.Event | None = None,
    ) -> None:
        self._server = server
        self._auth_manager = auth_manager
        self._config = config
        self._stop_event = stop_event or threading.Event()
        self._debug_mode = False
        self._icon: pystray.Icon | None = None

    def run(self) -> None:
        """Start the tray icon (blocking)."""
        self._icon = pystray.Icon(
            name="RemotePad",
            icon=_create_icon_image("green"),
            title=f"RemotePad v{__version__}",
            menu=self._create_menu(),
        )
        self._icon.run(setup=self._on_setup)

    def _create_menu(self) -> pystray.Menu:
        return pystray.Menu(
            pystray.MenuItem(f"RemotePad v{__version__}", None, enabled=False),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Show PIN", self._show_pin),
            pystray.MenuItem(
                "Debug mode",
                self._toggle_debug,
                checked=lambda item: self._debug_mode,
            ),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Quit", self._quit),
        )

    # -- Actions ------------------------------------------------------------

    def _on_setup(self, icon: pystray.Icon) -> None:
        """Called when the tray icon is ready."""
        icon.visible = True
        pin = self._auth_manager.get_pin()
        ip = _get_local_ip()
        port = self._config.port
        msg = f"PIN: {pin}\nIP: {ip}:{port}"
        icon.notify(msg, title="RemotePad")
        # Never log the PIN or the LAN IP: both are surfaced through the
        # tray notification above, which is the only intended channel.
        logger.info("Tray started on port %d", port)

    def _show_pin(self) -> None:
        pin = self._auth_manager.get_pin()
        ip = _get_local_ip()
        port = self._config.port
        msg = f"PIN: {pin}\nIP: {ip}:{port}"
        if self._icon:
            self._icon.notify(msg, title="RemotePad")

    def _toggle_debug(self) -> None:
        self._debug_mode = not self._debug_mode
        set_debug_mode(self._debug_mode)
        level = "DEBUG" if self._debug_mode else "INFO"
        logger.info("Debug mode: %s", level)

    def _quit(self) -> None:
        logger.info("Quit requested from tray")
        self._stop_event.set()
        if self._icon:
            self._icon.stop()
