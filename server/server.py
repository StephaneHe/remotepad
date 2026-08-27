"""RemotePad WebSocket server (Step 1.5).

Orchestrates WebSocket connections, authentication, and input dispatch.
"""

from __future__ import annotations

import asyncio
import json
import logging

import websockets
from websockets.asyncio.server import Server, ServerConnection

from server.auth_manager import AuthManager
from server.input_controller import InputController
from server.messages import (
    AuthRequest,
    AuthResponse,
    parse_message,
    serialize_message,
)
from server.screen_capture import capture_cursor_region

logger = logging.getLogger("remotepad.server")

# Maximum accepted size for a single inbound WebSocket message (64 KiB).
# Legitimate control/auth/text messages are well under this.
MAX_MESSAGE_BYTES = 64 * 1024


class RemotePadServer:
    """WebSocket server that authenticates clients and dispatches input."""

    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 9876,
        auth_manager: AuthManager | None = None,
        input_controller: InputController | None = None,
    ) -> None:
        self.host = host
        self._requested_port = port
        self.port: int = port
        self.auth_manager = auth_manager or AuthManager()
        self.input_controller = input_controller or InputController()

        self._server: Server | None = None
        self._authenticated_ws: ServerConnection | None = None

    # -- Lifecycle -----------------------------------------------------------

    @property
    def is_running(self) -> bool:
        return self._server is not None

    async def start(self) -> None:
        """Start the WebSocket server."""
        self._server = await websockets.serve(
            self._handle_client,
            self.host,
            self._requested_port,
            # Bound inbound frame size to prevent a malicious/buggy client
            # from exhausting memory with an oversized message (e.g. a huge
            # text_input payload).
            max_size=MAX_MESSAGE_BYTES,
        )
        # Resolve actual port (useful when port=0)
        sockets = self._server.sockets
        if sockets:
            self.port = sockets[0].getsockname()[1]
        logger.info("Server listening on %s:%d", self.host, self.port)

    async def stop(self) -> None:
        """Stop the server gracefully."""
        if self._server is not None:
            self._server.close()
            await self._server.wait_closed()
            self._server = None
            logger.info("Server stopped")

    # -- Connection handler --------------------------------------------------

    async def _handle_client(self, websocket: ServerConnection) -> None:
        """Handle a single client connection lifecycle."""
        client_ip = websocket.remote_address[0] if websocket.remote_address else "unknown"
        logger.info("Client connected from %s", client_ip)
        authenticated = False
        stream_task = None

        try:
            async for raw_message in websocket:
                try:
                    msg = parse_message(raw_message)
                except (ValueError, KeyError) as exc:
                    logger.error("Malformed message from %s: %s", client_ip, exc)
                    continue

                if isinstance(msg, AuthRequest):
                    response = self.auth_manager.verify(msg.pin, client_ip)
                    await websocket.send(serialize_message(response))
                    if response.success:
                        authenticated = True
                        await self._set_current_client(websocket)
                        if stream_task is None:
                            stream_task = asyncio.create_task(
                                self._stream_cursor_region(websocket)
                            )
                    continue

                if not authenticated:
                    error_resp = AuthResponse(
                        success=False,
                        message="Authentication required",
                    )
                    await websocket.send(serialize_message(error_resp))
                    continue

                # Authenticated command -- dispatch to input controller
                try:
                    self.input_controller.handle(msg)
                except Exception as exc:
                    logger.error("InputController error: %s", exc)

        except websockets.exceptions.ConnectionClosed:
            pass

        finally:
            if stream_task is not None:
                stream_task.cancel()
                try:
                    await stream_task
                except asyncio.CancelledError:
                    pass
            if self._authenticated_ws is websocket:
                self._authenticated_ws = None
            logger.info("Client disconnected from %s", client_ip)

    # -- Cursor region streaming ---------------------------------------------

    async def _stream_cursor_region(self, websocket: ServerConnection) -> None:
        """Periodically capture and send cursor region as binary JPEG frames."""
        loop = asyncio.get_running_loop()
        try:
            while True:
                try:
                    jpeg_bytes = await loop.run_in_executor(
                        None, capture_cursor_region
                    )
                    await websocket.send(jpeg_bytes)
                except websockets.exceptions.ConnectionClosed:
                    break
                except Exception as exc:
                    logger.error("Cursor region error: %s", exc)
                await asyncio.sleep(0.1)  # ~10 FPS
        except asyncio.CancelledError:
            pass

    # -- Single-client management -------------------------------------------

    async def _set_current_client(self, websocket: ServerConnection) -> None:
        """Register *websocket* as the sole authenticated client.

        If another client is already connected, close it first.
        """
        if self._authenticated_ws is not None and self._authenticated_ws is not websocket:
            try:
                await self._authenticated_ws.close(1000, "Replaced by new client")
            except Exception:
                pass
        self._authenticated_ws = websocket
