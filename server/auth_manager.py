"""Authentication manager for RemotePad (Step 1.4).

Handles PIN generation, verification with rate-limiting,
and per-IP lockout after repeated failures.
"""

from __future__ import annotations

import logging
import random
import time

from server.messages import AuthResponse

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

MAX_ATTEMPTS = 3
LOCKOUT_SECONDS = 30
PIN_MIN = 1000
PIN_MAX = 9999


# ---------------------------------------------------------------------------
# AuthManager
# ---------------------------------------------------------------------------

class AuthManager:
    """Manages PIN-based authentication with per-IP rate limiting."""

    def __init__(self) -> None:
        self._pin: str = self._generate_pin()
        self._failed_attempts: dict[str, int] = {}
        self._lockout_until: dict[str, float] = {}

    # -- PIN management -----------------------------------------------------

    @staticmethod
    def _generate_pin() -> str:
        return str(random.randint(PIN_MIN, PIN_MAX))

    def get_pin(self) -> str:
        """Return the current PIN."""
        return self._pin

    def regenerate_pin(self) -> str:
        """Generate a new PIN and return it."""
        self._pin = self._generate_pin()
        return self._pin

    # -- Lockout management -------------------------------------------------

    def is_locked(self, client_ip: str) -> bool:
        """Return True if *client_ip* is currently locked out."""
        lockout_end = self._lockout_until.get(client_ip)
        if lockout_end is None:
            return False
        if time.monotonic() >= lockout_end:
            # Lockout expired — reset state for this IP
            self._lockout_until.pop(client_ip, None)
            self._failed_attempts.pop(client_ip, None)
            return False
        return True

    # -- Verification -------------------------------------------------------

    def verify(self, pin: str, client_ip: str) -> AuthResponse:
        """Verify a PIN from *client_ip* and return an AuthResponse."""

        # Check lockout first
        if self.is_locked(client_ip):
            remaining = self._lockout_until[client_ip] - time.monotonic()
            remaining = max(0, int(remaining))
            msg = f"Blocked for {remaining}s"
            logger.warning("Client %s blocked (%ds remaining)", client_ip, remaining)
            return AuthResponse(success=False, message=msg)

        if pin == self._pin:
            # Success — reset failure counter
            self._failed_attempts.pop(client_ip, None)
            logger.info("Auth OK from %s", client_ip)
            return AuthResponse(success=True, message="Authenticated")

        # Failure path
        attempts = self._failed_attempts.get(client_ip, 0) + 1
        self._failed_attempts[client_ip] = attempts
        logger.warning(
            "Auth FAILED from %s (attempt %d/%d)", client_ip, attempts, MAX_ATTEMPTS
        )

        if attempts >= MAX_ATTEMPTS:
            self._lockout_until[client_ip] = time.monotonic() + LOCKOUT_SECONDS
            logger.warning("Client %s blocked for %ds", client_ip, LOCKOUT_SECONDS)

        return AuthResponse(success=False, message="Invalid PIN")
