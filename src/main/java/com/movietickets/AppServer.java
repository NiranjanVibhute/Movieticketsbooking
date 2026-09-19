package com.movietickets;

import com.movietickets.controller.MovieController;
import com.movietickets.db.DatabaseConnector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class AppServer {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        try {
            int port = DEFAULT_PORT;
            String portEnv = System.getenv("PORT");
            if (portEnv != null && !portEnv.isEmpty()) {
                try {
                    port = Integer.parseInt(portEnv);
                } catch (NumberFormatException ignored) {}
            }

            // Initialize DB
            System.out.println("==================================================");
            System.out.println("  DARK-OPS CINEMA: MOVIE TICKET BOOKING SYSTEM");
            System.out.println("==================================================");
            System.out.println("[AppServer] Initializing SQLite database...");
            DatabaseConnector.getInstance();

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            MovieController movieController = new MovieController();

            // API handler
            server.createContext("/api", movieController::handle);

            // Static files handler
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(Executors.newFixedThreadPool(16));
            server.start();

            System.out.println("[AppServer] Server started successfully!");
            System.out.println("[AppServer] >> Dashboard:     http://localhost:" + port + "/index.html");
            System.out.println("[AppServer] >> Booking POV:   http://localhost:" + port + "/booking.html");
            System.out.println("[AppServer] >> Snack Lab:     http://localhost:" + port + "/snacks.html");
            System.out.println("[AppServer] >> Confirmation:  http://localhost:" + port + "/confirmation.html");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("[AppServer] Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private final Path webRoot = Paths.get("web").toAbsolutePath();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/") || pathStr.isEmpty()) {
                pathStr = "/index.html";
            }

            // Prevent path traversal
            Path filePath = webRoot.resolve(pathStr.substring(1)).normalize();
            if (!filePath.startsWith(webRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                String notFound = "<h1>404 Not Found</h1><p>Resource not found on DarkOps Cinema Server.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            String contentType = getMimeType(filePath.getFileName().toString());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getMimeType(String filename) {
            if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
            if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
            if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
            if (filename.endsWith(".png")) return "image/png";
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
            if (filename.endsWith(".svg")) return "image/svg+xml";
            if (filename.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
