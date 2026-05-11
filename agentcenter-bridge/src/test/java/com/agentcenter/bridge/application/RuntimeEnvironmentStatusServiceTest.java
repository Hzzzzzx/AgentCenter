package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class RuntimeEnvironmentStatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void currentStatusReportsUnifiedWorkingDirectory() {
        RuntimeEnvironmentStatusService service = new RuntimeEnvironmentStatusService(
                new ObjectMapper(),
                "OPENCODE",
                true,
                "127.0.0.1",
                4097,
                tempDir.toString(),
                request -> new JsonResponse(request, 200, """
                        {"directory":"%s","worktree":"%s"}
                        """.formatted(tempDir, tempDir))
        );

        var status = service.currentStatus();

        assertThat(status.serverReachable()).isTrue();
        assertThat(status.workingDirectory()).isEqualTo(tempDir.toString());
        assertThat(status.workspaceAligned()).isTrue();
        assertThat(status.serverUrl()).isEqualTo("http://127.0.0.1:4097");
    }

    private record JsonResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
