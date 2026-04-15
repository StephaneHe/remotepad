"""RemotePad server entry point.

Usage:
    python -m server       (development)
    RemotePad.exe          (packaged)
"""

from __future__ import annotations

import asyncio
import logging
import os
import sys
import threading

from server.auth_manager import AuthManager
from server.config import Config
from server.input_controller import InputController
from server.log_manager import setup_logging
from server.server import RemotePadServer
from server.tray import TrayApp


def get_app_dir() -> str:
    """Return the application directory.

    When frozen (PyInstaller .exe): the directory containing the .exe.
    When running as a script: the project root (one level above server/).
    """
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def _run_server(server: RemotePadServer, stop_event: threading.Event) -> None:
    """Run the async WebSocket server in a dedicated thread."""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    logger = logging.getLogger("remotepad")

    async def _serve() -> None:
        await server.start()
        logger.info("WebSocket server started")
        while not stop_event.is_set():
            await asyncio.sleep(0.5)
        await server.stop()
        logger.info("WebSocket server stopped")

    loop.run_until_complete(_serve())
    loop.close()


def main() -> None:
    app_dir = get_app_dir()
    config_path = os.path.join(app_dir, "config.json")
    log_path = os.path.join(app_dir, "remotepad.log")

    # Load configuration
    config = Config(os.path.abspath(config_path))
    config.load()

    # Setup logging
    log_level = getattr(logging, config.log_level.upper(), logging.INFO)
    setup_logging(log_file=os.path.abspath(log_path), level=log_level)
    logger = logging.getLogger("remotepad")

    # Create components
    auth = AuthManager()
    controller = InputController()
    server = RemotePadServer("0.0.0.0", config.port, auth, controller)

    # Shared stop event
    stop_event = threading.Event()

    # Start server in background thread
    server_thread = threading.Thread(
        target=_run_server,
        args=(server, stop_event),
        daemon=True,
    )
    server_thread.start()

    # Run tray in main thread (required by pystray on Windows)
    tray = TrayApp(server, auth, config, stop_event)
    logger.info("PIN: %s", auth.get_pin())
    tray.run()

    # Tray exited — ensure server stops
    stop_event.set()
    server_thread.join(timeout=5)
    logger.info("RemotePad shut down")


if __name__ == "__main__":
    main()
