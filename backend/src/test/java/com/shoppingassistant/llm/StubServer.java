package com.shoppingassistant.llm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

/** A tiny local HTTP server that records requests and replies with canned responses. */
class StubServer implements AutoCloseable {
    record Recorded(String path, Map<String, List<String>> headers, String body) {
    }

    record Reply(int status, String body) {
    }

    private final HttpServer server;
    final List<Recorded> requests = new ArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (this) {
                requests.add(new Recorded(exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body));
            }
            Reply reply = replies.isEmpty() ? new Reply(500, "{\"error\":\"no stub\"}") : replies.poll();
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    StubServer reply(int status, String body) {
        replies.add(new Reply(status, body));
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
