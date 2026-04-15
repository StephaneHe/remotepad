"""Tests for the configuration manager (Step 1.6 - RED phase)."""

import json
import os

import pytest

from server.config import Config


@pytest.fixture
def config_path(tmp_path):
    return str(tmp_path / "config.json")


@pytest.fixture
def config(config_path):
    return Config(config_path)


class TestConfigDefaults:
    def test_config_defaults(self, config):
        """When no file exists, defaults are applied."""
        config.load()
        assert config.port == 9876
        assert config.log_level == "INFO"
        assert config.auto_start is False

    def test_config_defaults_creates_file(self, config, config_path):
        """Loading with no file creates config.json with defaults."""
        config.load()
        assert os.path.exists(config_path)
        with open(config_path, encoding="utf-8") as f:
            data = json.load(f)
        assert data["port"] == 9876


class TestConfigLoad:
    def test_config_load(self, config, config_path):
        """Existing JSON file is read correctly."""
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump({"port": 5555, "log_level": "DEBUG", "auto_start": True}, f)
        config.load()
        assert config.port == 5555
        assert config.log_level == "DEBUG"
        assert config.auto_start is True

    def test_config_missing_key(self, config, config_path):
        """A missing key in JSON falls back to the default value."""
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump({"port": 4000}, f)
        config.load()
        assert config.port == 4000
        assert config.log_level == "INFO"  # default
        assert config.auto_start is False  # default


class TestConfigSave:
    def test_config_save(self, config, config_path):
        """Modifying port and saving persists to the file."""
        config.load()
        config.port = 7777
        config.save()
        with open(config_path, encoding="utf-8") as f:
            data = json.load(f)
        assert data["port"] == 7777


class TestConfigValidation:
    def test_config_invalid_port_low(self, config):
        config.load()
        with pytest.raises(ValueError):
            config.port = 1023

    def test_config_invalid_port_high(self, config):
        config.load()
        with pytest.raises(ValueError):
            config.port = 65536

    def test_config_valid_port_bounds(self, config):
        config.load()
        config.port = 1024
        assert config.port == 1024
        config.port = 65535
        assert config.port == 65535
