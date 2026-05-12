package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.net.URI;
import java.util.Locale;

import com.agentcenter.bridge.application.runtime.transport.RuntimeTransportException;

/**
 * Centralized URI construction for OpenCode serve endpoints.
 */
public final class OpenCodeEndpoint {

    private OpenCodeEndpoint() {
    }

    public static URI uri(String baseUrl, String path) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, path);
        String normalizedPath = normalizePath(baseUrl, path);
        try {
            return URI.create(normalizedBaseUrl + normalizedPath);
        } catch (IllegalArgumentException e) {
            throw invalid(baseUrl, path, e);
        }
    }

    private static String normalizeBaseUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw invalid(baseUrl, path, null);
        }
        String trimmed = baseUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw invalid(baseUrl, path, null);
        }

        URI parsed;
        try {
            parsed = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw invalid(baseUrl, path, e);
        }
        if (parsed.getScheme() == null || parsed.getHost() == null
                || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
            throw invalid(baseUrl, path, null);
        }

        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String normalizePath(String baseUrl, String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw invalid(baseUrl, path, null);
        }
        return path;
    }

    private static RuntimeTransportException invalid(String baseUrl, String path, Throwable cause) {
        return new RuntimeTransportException(
                "Invalid OpenCode serve endpoint: baseUrl must be an http(s) URL and path must start with '/'. "
                        + "baseUrl=" + summarize(baseUrl) + ", path=" + summarize(path),
                cause,
                false);
    }

    private static String summarize(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 120) {
            return "'" + normalized + "'";
        }
        return "'" + normalized.substring(0, 117) + "...'";
    }
}
