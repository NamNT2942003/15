package com.quickbite.cart;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public class CartServiceApplication {
    private static final AtomicLong cartRequests = new AtomicLong(0);

    public static void main(String[] args) throws IOException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        System.out.println(now + "  INFO 1 --- [main] c.q.c.CartServiceApplication         : Starting QuickBite CartService with Spring Boot Actuator...");

        int port = 8080;
        String portEnv = System.getenv("SERVER_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try { port = Integer.parseInt(portEnv); } catch (Exception ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // 1. Endpoint nghiệp vụ /api/v1/cart
        server.createContext("/api/v1/cart", exchange -> {
            cartRequests.incrementAndGet();
            String response = "{\"status\":\"SUCCESS\",\"service\":\"cart-service\",\"items\":[]}";
            sendResponse(exchange, 200, "application/json", response);
        });

        // 2. Endpoint /actuator/health
        server.createContext("/actuator/health", exchange -> {
            String response = "{\"status\":\"UP\",\"components\":{\"diskSpace\":{\"status\":\"UP\"},\"ping\":{\"status\":\"UP\"}}}";
            sendResponse(exchange, 200, "application/json", response);
        });

        // 3. Endpoint /actuator/info
        server.createContext("/actuator/info", exchange -> {
            String response = "{\"app\":{\"name\":\"quickbite-cart-service\",\"version\":\"1.0.0\"}}";
            sendResponse(exchange, 200, "application/json", response);
        });

        // 4. Endpoint /actuator/prometheus (Đã mở khóa phơi bày metrics - Bài tập 5)
        server.createContext("/actuator/prometheus", exchange -> {
            Runtime rt = Runtime.getRuntime();
            long usedMemory = rt.totalMemory() - rt.freeMemory();
            long totalMemory = rt.totalMemory();
            long maxMemory = rt.maxMemory();
            int threads = Thread.activeCount();

            StringBuilder sb = new StringBuilder();
            sb.append("# HELP jvm_memory_used_bytes The amount of used memory\n");
            sb.append("# TYPE jvm_memory_used_bytes gauge\n");
            sb.append("jvm_memory_used_bytes{area=\"heap\",id=\"G1 Survivor Space\"} ").append(usedMemory).append("\n");
            sb.append("jvm_memory_committed_bytes{area=\"heap\"} ").append(totalMemory).append("\n");
            sb.append("jvm_memory_max_bytes{area=\"heap\"} ").append(maxMemory).append("\n\n");

            sb.append("# HELP jvm_threads_live_threads The current number of live threads\n");
            sb.append("# TYPE jvm_threads_live_threads gauge\n");
            sb.append("jvm_threads_live_threads ").append(threads).append("\n\n");

            sb.append("# HELP http_server_requests_seconds_count Number of HTTP requests\n");
            sb.append("# TYPE http_server_requests_seconds_count counter\n");
            sb.append("http_server_requests_seconds_count{application=\"cart-service\",uri=\"/api/v1/cart\",status=\"200\",method=\"GET\"} ").append(cartRequests.get()).append("\n");

            sendResponse(exchange, 200, "text/plain; version=0.0.4; charset=utf-8", sb.toString());
        });

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println(now + "  INFO 1 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port: " + port + " (http)");
        System.out.println(now + "  INFO 1 --- [main] c.q.c.CartServiceApplication         : Started CartServiceApplication successfully!");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
