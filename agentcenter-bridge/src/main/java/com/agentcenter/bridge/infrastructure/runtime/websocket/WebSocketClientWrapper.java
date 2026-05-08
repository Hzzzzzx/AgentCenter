package com.agentcenter.bridge.infrastructure.runtime.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin wrapper around {@link java.net.http.WebSocket} (JDK 11+ built-in).
 * Provides connect/send/close with a message callback.
 */
public class WebSocketClientWrapper {

    private static final Logger LOG = Logger.getLogger(WebSocketClientWrapper.class.getName());

    private final URI serverUri;
    private final Consumer<String> onMessage;
    private final Runnable onClose;
    private final Consumer<Throwable> onError;
    private final HttpClient httpClient;
    private volatile WebSocket webSocket;

    public WebSocketClientWrapper(URI serverUri, Consumer<String> onMessage) {
        this(serverUri, onMessage, () -> {}, t -> LOG.log(Level.WARNING, "WebSocket error", t));
    }

    public WebSocketClientWrapper(URI serverUri, Consumer<String> onMessage,
                                  Runnable onClose, Consumer<Throwable> onError) {
        this.serverUri = serverUri;
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.onError = onError;
        this.httpClient = HttpClient.newHttpClient();
    }

    public CompletableFuture<Void> connect() {
        return httpClient.newWebSocketBuilder()
            .buildAsync(serverUri, new WebSocketListener())
            .thenAccept(ws -> {
                this.webSocket = ws;
                LOG.info(() -> "WebSocket connected to " + serverUri);
            });
    }

    public CompletableFuture<WebSocket> send(String message) {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected"));
        }
        return ws.sendText(message, true);
    }

    public void close() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "client closing");
            this.webSocket = null;
        }
    }

    private class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    onMessage.accept(message);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error processing WebSocket message", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.info(() -> "WebSocket closed: " + statusCode + " " + reason);
            WebSocketClientWrapper.this.webSocket = null;
            onClose.run();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.log(Level.WARNING, "WebSocket error", error);
            WebSocketClientWrapper.this.webSocket = null;
            onError.accept(error);
        }
    }
}
