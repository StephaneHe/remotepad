"""Tests for the auth manager (Step 1.4 - RED phase)."""

import time
import logging
from unittest.mock import patch

import pytest

from server.auth_manager import AuthManager
from server.messages import AuthResponse


@pytest.fixture
def manager():
    return AuthManager()


class TestGeneratePin:
    def test_generate_pin(self, manager):
        pin = manager.get_pin()
        assert pin.isdigit()
        assert len(pin) == 4
        assert 1000 <= int(pin) <= 9999

    def test_generate_pin_randomness(self):
        """Two separate managers should not always produce the same PIN."""
        pins = {AuthManager().get_pin() for _ in range(20)}
        # With 20 tries, we should get at least 2 different PINs
        assert len(pins) > 1

    def test_regenerate_pin(self, manager):
        old_pin = manager.get_pin()
        # Regenerate many times; at least one should differ
        new_pins = {manager.regenerate_pin() for _ in range(20)}
        # The new pin is always 4 digits
        for p in new_pins:
            assert len(p) == 4 and p.isdigit()


class TestPinIsCryptographic:
    def test_generate_pin_uses_csprng(self):
        """PIN generation must go through secrets.randbelow, not random."""
        with patch("server.auth_manager.secrets.randbelow", return_value=234) as mock:
            pin = AuthManager().get_pin()
        mock.assert_called()
        assert pin == "1234"  # PIN_MIN (1000) + 234

    def test_verify_uses_constant_time_compare(self, manager):
        """Verification must use hmac.compare_digest, not ==."""
        pin = manager.get_pin()
        with patch(
            "server.auth_manager.hmac.compare_digest", return_value=True
        ) as mock:
            result = manager.verify(pin, "192.168.1.99")
        mock.assert_called_once()
        assert result.success is True


class TestAuthSuccess:
    def test_auth_success(self, manager):
        pin = manager.get_pin()
        result = manager.verify(pin, "192.168.1.20")
        assert isinstance(result, AuthResponse)
        assert result.success is True

    def test_auth_success_resets_counter(self, manager):
        pin = manager.get_pin()
        # Fail twice then succeed
        manager.verify("0000", "192.168.1.20")
        manager.verify("0000", "192.168.1.20")
        result = manager.verify(pin, "192.168.1.20")
        assert result.success is True
        # Now fail again — counter should be back to 0, so 1st failure
        result2 = manager.verify("0000", "192.168.1.20")
        assert result2.success is False
        # Should NOT be locked yet (only 1 failure after reset)
        assert not manager.is_locked("192.168.1.20")


class TestAuthFailure:
    def test_auth_failure(self, manager):
        result = manager.verify("0000", "192.168.1.20")
        assert isinstance(result, AuthResponse)
        assert result.success is False


class TestAuthLockout:
    def test_auth_lockout(self, manager):
        ip = "192.168.1.30"
        for _ in range(3):
            manager.verify("0000", ip)
        assert manager.is_locked(ip)
        # 4th attempt should be rejected immediately
        result = manager.verify(manager.get_pin(), ip)
        assert result.success is False

    def test_auth_lockout_message(self, manager):
        ip = "192.168.1.31"
        for _ in range(3):
            manager.verify("0000", ip)
        result = manager.verify("0000", ip)
        assert result.success is False
        # Message should mention seconds remaining or "blocked"
        assert "block" in result.message.lower() or "sec" in result.message.lower()

    def test_auth_lockout_reset(self, manager):
        ip = "192.168.1.32"
        for _ in range(3):
            manager.verify("0000", ip)
        assert manager.is_locked(ip)
        # Simulate time passing beyond lockout
        with patch("server.auth_manager.time") as mock_time:
            # First call is during lockout check, set time far in the future
            mock_time.monotonic.return_value = time.monotonic() + 60
            assert not manager.is_locked(ip)
            result = manager.verify(manager.get_pin(), ip)
            assert result.success is True


class TestAuthLogging:
    def test_auth_logged_success(self, manager, caplog):
        pin = manager.get_pin()
        with caplog.at_level(logging.INFO):
            manager.verify(pin, "10.0.0.1")
        assert any("Auth OK" in r.message and "10.0.0.1" in r.message for r in caplog.records)

    def test_auth_logged_failure(self, manager, caplog):
        with caplog.at_level(logging.WARNING):
            manager.verify("0000", "10.0.0.2")
        assert any("Auth FAILED" in r.message and "10.0.0.2" in r.message for r in caplog.records)
