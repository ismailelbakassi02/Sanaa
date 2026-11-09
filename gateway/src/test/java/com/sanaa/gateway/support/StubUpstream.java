package com.sanaa.gateway.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal HTTP server standing in for a backend service.
 *
 * <p>WHY the JDK server: the test only needs to record the incoming request and answer 200. Using
 * the JDK keeps the dependency list unchanged and avoids a test-only HTTP mock library version
 * that the BOMs do not manage.
 */
public final class StubUpstream implements AutoCloseable {

    public record Recorded(String method, String path, Map<String, List<String>> headers) {

        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final String responseBody;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    private StubUpstream(HttpServer server, ExecutorService executor, String responseBody) {
        this.server = server;
        this.executor = executor;
        this.responseBody = responseBody;
    }

    /**
     * Starts a stub that answers every request with {@code {"service":"<name>"}}.
     */
    public static StubUpstream service(String name) {
        return start("{\"service\":\"" + name + "\"}");
    }

    /**
     * Starts a stub that answers every request with a fixed JSON document, used for the JWKS.
     */
    public static StubUpstream json(String fixedJson) {
        return start(fixedJson);
    }

    private static StubUpstream start(String fixedBody) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            StubUpstream stub = new StubUpstream(server, executor, fixedBody);
            server.createContext("/", stub::handle);
            server.setExecutor(executor);
            server.start();
            return stub;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
        requests.add(new Recorded(exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(), headers));

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return List.copyOf(requests);
    }

    public int requestCount() {
        return requests.size();
    }

    public void clear() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
