package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class RuntimeEnvironmentStatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void currentStatusReportsUnifiedWorkingDirectory() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/path", exchange -> {
            String body = """
                    {"directory":"%s","worktree":"%s"}
                    """.formatted(tempDir, tempDir);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        try {
            RuntimeEnvironmentStatusService service = new RuntimeEnvironmentStatusService(
                    new ObjectMapper(),
                    "OPENCODE",
                    true,
                    "127.0.0.1",
                    server.getAddress().getPort(),
                    tempDir.toString()
            );

            var status = service.currentStatus();

            assertThat(status.serverReachable()).isTrue();
            assertThat(status.workingDirectory()).isEqualTo(tempDir.toString());
            assertThat(status.workspaceAligned()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
