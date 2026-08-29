package com.islamshariful.authservice.service;

/**
 * Where a token was issued from, recorded against each refresh token so a user can be shown their active
 * sessions and an incident can be traced to a device.
 *
 * <p>Both values are attacker-controlled (the IP only when a proxy is misconfigured), so they are truncated
 * to their column widths here and never interpolated into anything but a parameterised insert.
 */
public record ClientMetadata(String userAgent, String ipAddress) {

    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_IP = 45;

    public static ClientMetadata of(String userAgent, String ipAddress) {
        return new ClientMetadata(truncate(userAgent, MAX_USER_AGENT), truncate(ipAddress, MAX_IP));
    }

    public static ClientMetadata unknown() {
        return new ClientMetadata(null, null);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
