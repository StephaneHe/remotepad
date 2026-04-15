package com.remotepad.viewmodel

/**
 * Reusable input validators for the connection form.
 */
object InputValidator {

    private val IPV4_REGEX = Regex(
        """^((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)$"""
    )

    private val HOSTNAME_REGEX = Regex(
        """^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$"""
    )

    fun isValidIp(ip: String): Boolean = IPV4_REGEX.matches(ip.trim())

    fun isValidHost(host: String): Boolean {
        val trimmed = host.trim()
        return trimmed.isNotEmpty() && (IPV4_REGEX.matches(trimmed) || HOSTNAME_REGEX.matches(trimmed))
    }

    fun isValidPort(port: String): Boolean {
        val n = port.trim().toIntOrNull() ?: return false
        return n in 1024..65535
    }

    fun isValidPin(pin: String): Boolean {
        return pin.trim().length == 4 && pin.trim().all { it.isDigit() }
    }
}
