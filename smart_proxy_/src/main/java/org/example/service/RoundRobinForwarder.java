package org.example.service;

import com.sun.net.httpserver.HttpExchange;
import org.example.models.DwResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class RoundRobinForwarder {
    private static final Logger logger = Logger.getLogger(RoundRobinForwarder.class.getName());

    private final List<String> dataWarehouses;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public RoundRobinForwarder(List<String> dataWarehouses){
        this.dataWarehouses = dataWarehouses;
    }

    private String nextDataWarehouse() {
        int number = counter.getAndIncrement();
        int index = Math.floorMod(number, dataWarehouses.size());

        return dataWarehouses.get(index);
    }

    public DwResponse forward(HttpExchange exchange) {
        try {
            String server = nextDataWarehouse();
            String url = server + exchange.getRequestURI();
            String method = exchange.getRequestMethod();
            byte[] body = exchange.getRequestBody().readAllBytes();

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .method(method, body.length == 0
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            copyHeader(exchange, builder, "Accept");
            copyHeader(exchange, builder, "Content-Type");

            logger.info("Отправляю " + method + " на " + url);
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            return new DwResponse(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    new String(response.body(), StandardCharsets.UTF_8));

        } catch (IOException | InterruptedException e) {
            logger.warning("DW недоступен: " + e.getMessage());
            return new DwResponse(502, "text/plain", "DW server is not available");
        }
    }


    private void copyHeader(HttpExchange exchange, HttpRequest.Builder builder, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value != null) {
            builder.header(name, value);
        }
    }

    private void sendToClient(HttpExchange exchange, int status, byte[] body) throws IOException {
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
