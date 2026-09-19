package com.movietickets.controller;

import com.movietickets.db.DatabaseConnector;
import com.movietickets.engine.BookingEngine;
import com.movietickets.engine.BookingEngine.SeatData;
import com.movietickets.engine.BookingEngine.SnackItem;
import com.movietickets.engine.BookingEngine.SplitPaySession;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class MovieController {
    private final DatabaseConnector db = DatabaseConnector.getInstance();
    private final BookingEngine engine = BookingEngine.getInstance();

    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQuery(query);

        try {
            if (path.equals("/api/movies") && "GET".equalsIgnoreCase(method)) {
                handleGetMovies(exchange);
            } else if (path.startsWith("/api/movies/") && "GET".equalsIgnoreCase(method)) {
                String movieId = path.substring("/api/movies/".length());
                handleGetMovieById(exchange, movieId);
            } else if (path.equals("/api/seats") && "GET".equalsIgnoreCase(method)) {
                handleGetSeats(exchange, queryParams);
            } else if (path.equals("/api/seats/heatmap") && "GET".equalsIgnoreCase(method)) {
                handleGetVibeHeatmap(exchange, queryParams);
            } else if (path.equals("/api/seats/optimize") && "POST".equalsIgnoreCase(method)) {
                handleOptimizeSeats(exchange);
            } else if (path.equals("/api/seats/lock") && "POST".equalsIgnoreCase(method)) {
                handleLockSeats(exchange);
            } else if (path.equals("/api/split-pay/create") && "POST".equalsIgnoreCase(method)) {
                handleCreateSplitPay(exchange);
            } else if (path.equals("/api/split-pay/status") && "GET".equalsIgnoreCase(method)) {
                handleGetSplitPayStatus(exchange, queryParams);
            } else if (path.equals("/api/split-pay/pay") && "POST".equalsIgnoreCase(method)) {
                handleContributeSplitPay(exchange);
            } else if (path.equals("/api/snacks") && "GET".equalsIgnoreCase(method)) {
                handleGetSnacks(exchange);
            } else if (path.equals("/api/snacks/calculate") && "POST".equalsIgnoreCase(method)) {
                handleCalculateSnacks(exchange);
            } else if (path.equals("/api/bookings/checkout") && "POST".equalsIgnoreCase(method)) {
                handleCheckout(exchange);
            } else if (path.startsWith("/api/bookings/") && "GET".equalsIgnoreCase(method)) {
                String ref = path.substring("/api/bookings/".length());
                handleGetBooking(exchange, ref);
            } else {
                sendJsonResponse(exchange, 404, "{\"error\":\"Endpoint not found\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"error\":\"Internal Server Error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ==========================================
    // ENDPOINT HANDLERS
    // ==========================================

    private void handleGetMovies(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> movies = new ArrayList<>();
        String sql = "SELECT * FROM movies ORDER BY is_blind_box DESC, score DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("title", rs.getString("title"));
                m.put("genre", rs.getString("genre"));
                m.put("duration", rs.getInt("duration"));
                m.put("rating", rs.getString("rating"));
                m.put("score", rs.getDouble("score"));
                m.put("posterUrl", rs.getString("poster_url"));
                m.put("backdropUrl", rs.getString("backdrop_url"));
                m.put("synopsis", rs.getString("synopsis"));
                m.put("basePrice", rs.getDouble("base_price"));
                m.put("isBlindBox", rs.getInt("is_blind_box") == 1);
                m.put("blindBoxRevealTitle", rs.getString("blind_box_reveal_title"));
                m.put("blindBoxDiscountPct", rs.getInt("blind_box_discount_pct"));
                m.put("tags", rs.getString("tags"));
                movies.add(m);
            }
        }
        sendJsonResponse(exchange, 200, toJson(movies));
    }

    private void handleGetMovieById(HttpExchange exchange, String movieId) throws Exception {
        String sql = "SELECT * FROM movies WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("genre", rs.getString("genre"));
                    m.put("duration", rs.getInt("duration"));
                    m.put("rating", rs.getString("rating"));
                    m.put("score", rs.getDouble("score"));
                    m.put("posterUrl", rs.getString("poster_url"));
                    m.put("backdropUrl", rs.getString("backdrop_url"));
                    m.put("synopsis", rs.getString("synopsis"));
                    m.put("basePrice", rs.getDouble("base_price"));
                    m.put("isBlindBox", rs.getInt("is_blind_box") == 1);
                    m.put("blindBoxRevealTitle", rs.getString("blind_box_reveal_title"));
                    m.put("blindBoxDiscountPct", rs.getInt("blind_box_discount_pct"));
                    m.put("tags", rs.getString("tags"));
                    sendJsonResponse(exchange, 200, toJson(m));
                    return;
                }
            }
        }
        sendJsonResponse(exchange, 404, "{\"error\":\"Movie not found\"}");
    }

    private void handleGetSeats(HttpExchange exchange, Map<String, String> params) throws Exception {
        String movieId = params.getOrDefault("movieId", "mov-dune2");
        String showTime = params.getOrDefault("showTime", "18:30");

        List<SeatData> seats = loadSeatsFromDb(movieId, showTime);
        List<Map<String, Object>> seatList = new ArrayList<>();
        for (SeatData s : seats) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.id);
            map.put("row", s.row);
            map.put("number", s.number);
            map.put("tier", s.tier);
            map.put("price", s.price);
            map.put("isBooked", s.isBooked);
            map.put("isLocked", engine.isSeatLocked(s.id));
            map.put("vibeTag", s.vibeTag);
            seatList.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("movieId", movieId);
        response.put("showTime", showTime);
        response.put("seats", seatList);
        sendJsonResponse(exchange, 200, toJson(response));
    }

    private void handleGetVibeHeatmap(HttpExchange exchange, Map<String, String> params) throws Exception {
        String movieId = params.getOrDefault("movieId", "mov-dune2");
        String showTime = params.getOrDefault("showTime", "18:30");

        List<SeatData> seats = loadSeatsFromDb(movieId, showTime);
        BookingEngine.VibeHeatmap heatmap = engine.generateVibeHeatmap(seats);

        Map<String, Object> res = new HashMap<>();
        res.put("movieId", movieId);
        res.put("showTime", showTime);
        res.put("totalCapacity", heatmap.totalCapacity);
        res.put("bookedRatio", heatmap.bookedRatio);
        res.put("vibeDistribution", heatmap.vibeDistribution);
        res.put("zoneCounts", heatmap.zoneCounts);
        res.put("quietRecommendedRows", heatmap.quietRecommendedRows);
        res.put("excitedFanRecommendedRows", heatmap.excitedFanRecommendedRows);
        res.put("familyRecommendedRows", heatmap.familyRecommendedRows);
        res.put("seatVibeMap", heatmap.seatVibeMap);

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleOptimizeSeats(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);

        String movieId = (String) json.getOrDefault("movieId", "mov-dune2");
        String showTime = (String) json.getOrDefault("showTime", "18:30");
        int groupSize = json.containsKey("groupSize") ? ((Number) json.get("groupSize")).intValue() : 2;
        String preferredVibe = (String) json.getOrDefault("preferredVibe", "NONE");
        String preferredTier = (String) json.getOrDefault("preferredTier", "");

        List<SeatData> seats = loadSeatsFromDb(movieId, showTime);
        BookingEngine.OptimizationResult result = engine.findOptimalSeats(seats, groupSize, preferredVibe, preferredTier);

        Map<String, Object> res = new HashMap<>();
        res.put("found", result.found);
        res.put("row", result.row);
        res.put("score", result.score);
        res.put("reason", result.reason);

        List<Map<String, Object>> recSeats = new ArrayList<>();
        for (SeatData s : result.recommendedSeats) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.id);
            m.put("row", s.row);
            m.put("number", s.number);
            m.put("tier", s.tier);
            m.put("price", s.price);
            m.put("vibeTag", s.vibeTag);
            recSeats.add(m);
        }
        res.put("recommendedSeats", recSeats);

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleLockSeats(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);
        @SuppressWarnings("unchecked")
        List<String> seatIds = (List<String>) json.get("seatIds");
        int duration = json.containsKey("durationMinutes") ? ((Number) json.get("durationMinutes")).intValue() : 10;

        if (seatIds != null) {
            for (String sid : seatIds) {
                engine.lockSeat(sid, duration);
            }
        }
        sendJsonResponse(exchange, 200, "{\"status\":\"locked\",\"count\":" + (seatIds != null ? seatIds.size() : 0) + "}");
    }

    private void handleCreateSplitPay(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);

        String movieId = (String) json.getOrDefault("movieId", "mov-dune2");
        String showTime = (String) json.getOrDefault("showTime", "18:30");
        @SuppressWarnings("unchecked")
        List<String> seatIds = (List<String>) json.get("seatIds");
        double totalAmount = json.containsKey("totalAmount") ? ((Number) json.get("totalAmount")).doubleValue() : 0.0;
        int participants = json.containsKey("participants") ? ((Number) json.get("participants")).intValue() : 2;
        String creatorName = (String) json.getOrDefault("creatorName", "Organizer");

        if (seatIds == null || seatIds.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"No seats selected for split-pay\"}");
            return;
        }

        SplitPaySession session = engine.createSplitSession(movieId, showTime, seatIds, totalAmount, participants, creatorName);

        Map<String, Object> res = new HashMap<>();
        res.put("splitCode", session.splitCode);
        res.put("perPersonAmount", session.perPersonAmount);
        res.put("totalAmount", session.totalAmount);
        res.put("totalParticipants", session.totalParticipants);
        res.put("paidParticipants", session.paidParticipants);
        res.put("remainingSeconds", session.getRemainingSeconds());
        res.put("expiresAt", session.expiresAt);
        res.put("isCompleted", session.isCompleted);
        res.put("shareUrl", "/booking.html?split=" + session.splitCode);

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleGetSplitPayStatus(HttpExchange exchange, Map<String, String> params) throws Exception {
        String code = params.get("code");
        if (code == null) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing split code\"}");
            return;
        }

        SplitPaySession session = engine.getSplitSession(code);
        if (session == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Split session expired or not found\"}");
            return;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("splitCode", session.splitCode);
        res.put("movieId", session.movieId);
        res.put("showTime", session.showTime);
        res.put("seatIds", session.seatIds);
        res.put("perPersonAmount", session.perPersonAmount);
        res.put("totalAmount", session.totalAmount);
        res.put("totalParticipants", session.totalParticipants);
        res.put("paidParticipants", session.paidParticipants);
        res.put("paidCount", session.paidParticipants.size());
        res.put("remainingSeconds", session.getRemainingSeconds());
        res.put("isCompleted", session.isCompleted);

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleContributeSplitPay(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);

        String code = (String) json.get("code");
        String name = (String) json.getOrDefault("participantName", "Friend");

        boolean success = engine.contributeToSplit(code, name);
        if (!success) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Unable to contribute. Session expired or already full.\"}");
            return;
        }

        SplitPaySession session = engine.getSplitSession(code);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("isCompleted", session != null && session.isCompleted);
        res.put("paidCount", session != null ? session.paidParticipants.size() : 0);
        res.put("totalParticipants", session != null ? session.totalParticipants : 0);

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleGetSnacks(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM snacks";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> s = new HashMap<>();
                s.put("id", rs.getString("id"));
                s.put("name", rs.getString("name"));
                s.put("category", rs.getString("category"));
                s.put("basePrice", rs.getDouble("base_price"));
                s.put("baseCalories", rs.getInt("base_calories"));
                s.put("description", rs.getString("description"));
                s.put("imageUrl", rs.getString("image_url"));
                list.add(s);
            }
        }
        sendJsonResponse(exchange, 200, toJson(list));
    }

    private void handleCalculateSnacks(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);

        String baseType = (String) json.getOrDefault("baseType", "POPCORN");
        @SuppressWarnings("unchecked")
        List<String> addOns = (List<String>) json.get("addOns");

        SnackItem decorated = engine.buildSnack(baseType, addOns);

        Map<String, Object> res = new HashMap<>();
        res.put("description", decorated.getDescription());
        res.put("price", decorated.getPrice());
        res.put("calories", decorated.getCalories());
        res.put("category", decorated.getBaseCategory());

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleCheckout(HttpExchange exchange) throws Exception {
        String body = readRequestBody(exchange);
        Map<String, Object> json = parseSimpleJson(body);

        String movieId = (String) json.getOrDefault("movieId", "mov-dune2");
        String showTime = (String) json.getOrDefault("showTime", "18:30");
        @SuppressWarnings("unchecked")
        List<String> seatIds = (List<String>) json.get("seatIds");
        double totalAmount = json.containsKey("totalAmount") ? ((Number) json.get("totalAmount")).doubleValue() : 0.0;
        String vibePreference = (String) json.getOrDefault("vibePreference", "NONE");
        String customerName = (String) json.getOrDefault("customerName", "Valued Guest");
        String customerEmail = (String) json.getOrDefault("customerEmail", "guest@darkops.cinema");
        String snacksJson = json.containsKey("snacks") ? toJson(json.get("snacks")) : "[]";
        String splitCode = (String) json.get("splitCode");

        if (seatIds == null || seatIds.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"No seats provided for booking\"}");
            return;
        }

        String bookingRef = "TIX-" + (int)(1000 + Math.random() * 9000) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String bookingId = UUID.randomUUID().toString();
        String seatNumbersStr = String.join(", ", seatIds);

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Mark seats as booked
                String updateSeats = "UPDATE seats SET is_booked = 1, vibe_tag = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSeats)) {
                    for (String sid : seatIds) {
                        ps.setString(1, vibePreference.equals("NONE") ? "QUIET" : vibePreference);
                        ps.setString(2, sid);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // 2. Insert booking record
                String insertBooking = "INSERT INTO bookings (id, booking_ref, movie_id, show_time, seat_numbers, vibe_preference, total_amount, split_status, split_code, customer_name, customer_email, snacks_json, delivery_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertBooking)) {
                    ps.setString(1, bookingId);
                    ps.setString(2, bookingRef);
                    ps.setString(3, movieId);
                    ps.setString(4, showTime);
                    ps.setString(5, seatNumbersStr);
                    ps.setString(6, vibePreference);
                    ps.setDouble(7, totalAmount);
                    ps.setString(8, splitCode != null ? "COMPLETED" : "NONE");
                    ps.setString(9, splitCode);
                    ps.setString(10, customerName);
                    ps.setString(11, customerEmail);
                    ps.setString(12, snacksJson);
                    ps.setString(13, "CONFIRMED");
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Release seat locks
        engine.unlockSeats(seatIds);

        // Fetch movie info to check if blind box
        String movieTitle = "Blockbuster Screening";
        boolean isBlindBox = false;
        String revealTitle = null;

        String mSql = "SELECT title, is_blind_box, blind_box_reveal_title FROM movies WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(mSql)) {
            ps.setString(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    movieTitle = rs.getString("title");
                    isBlindBox = rs.getInt("is_blind_box") == 1;
                    revealTitle = rs.getString("blind_box_reveal_title");
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("bookingRef", bookingRef);
        res.put("movieId", movieId);
        res.put("movieTitle", movieTitle);
        res.put("isBlindBox", isBlindBox);
        res.put("revealedMovieTitle", isBlindBox ? revealTitle : movieTitle);
        res.put("showTime", showTime);
        res.put("seatNumbers", seatNumbersStr);
        res.put("vibePreference", vibePreference);
        res.put("totalAmount", totalAmount);
        res.put("customerName", customerName);
        res.put("deliveryStatus", "CONFIRMED");
        res.put("barcodeData", "DARK-" + bookingRef + "-SEC99");
        res.put("message", isBlindBox ? "Blind Box Mystery Movie Unlocked!" : "Booking Confirmed Successfully!");

        sendJsonResponse(exchange, 200, toJson(res));
    }

    private void handleGetBooking(HttpExchange exchange, String ref) throws Exception {
        String sql = "SELECT b.*, m.title, m.is_blind_box, m.blind_box_reveal_title, m.poster_url FROM bookings b JOIN movies m ON b.movie_id = m.id WHERE b.booking_ref = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> b = new HashMap<>();
                    b.put("bookingRef", rs.getString("booking_ref"));
                    b.put("movieId", rs.getString("movie_id"));
                    b.put("movieTitle", rs.getString("title"));
                    b.put("isBlindBox", rs.getInt("is_blind_box") == 1);
                    b.put("revealedMovieTitle", rs.getInt("is_blind_box") == 1 ? rs.getString("blind_box_reveal_title") : rs.getString("title"));
                    b.put("posterUrl", rs.getString("poster_url"));
                    b.put("showTime", rs.getString("show_time"));
                    b.put("seatNumbers", rs.getString("seat_numbers"));
                    b.put("vibePreference", rs.getString("vibe_preference"));
                    b.put("totalAmount", rs.getDouble("total_amount"));
                    b.put("customerName", rs.getString("customer_name"));
                    b.put("customerEmail", rs.getString("customer_email"));
                    b.put("bookingTime", rs.getString("booking_time"));
                    b.put("deliveryStatus", rs.getString("delivery_status"));
                    b.put("barcodeData", "DARK-" + rs.getString("booking_ref") + "-SEC99");
                    b.put("snacksJson", rs.getString("snacks_json"));
                    sendJsonResponse(exchange, 200, toJson(b));
                    return;
                }
            }
        }
        sendJsonResponse(exchange, 404, "{\"error\":\"Booking reference not found\"}");
    }

    // ==========================================
    // HELPERS & LIGHTWEIGHT JSON UTILS
    // ==========================================

    private List<SeatData> loadSeatsFromDb(String movieId, String showTime) throws SQLException {
        List<SeatData> seats = new ArrayList<>();
        String sql = "SELECT * FROM seats WHERE movie_id = ? AND show_time = ? ORDER BY row_letter ASC, seat_number ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movieId);
            ps.setString(2, showTime);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(new SeatData(
                            rs.getString("id"),
                            rs.getString("row_letter"),
                            rs.getInt("seat_number"),
                            rs.getString("tier"),
                            rs.getDouble("price"),
                            rs.getInt("is_booked") == 1,
                            rs.getString("vibe_tag")
                    ));
                }
            }
        }
        return seats;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                params.put(kv[0], "");
            }
        }
        return params;
    }

    // Lightweight robust JSON Serializer
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Robust JSON Parser for API payloads
    public static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }

        int len = json.length();
        int i = 0;
        while (i < len) {
            // Skip whitespace and commas
            while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= len) break;

            // Key must start with quote
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            i++; // skip open quote
            int keyStart = i;
            while (i < len && json.charAt(i) != '"') i++;
            String key = json.substring(keyStart, i);
            if (i < len) i++; // skip close quote

            // Skip until colon
            while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
            if (i >= len) break;

            char c = json.charAt(i);
            if (c == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < len) {
                        i++;
                        sb.append(json.charAt(i));
                    } else {
                        sb.append(json.charAt(i));
                    }
                    i++;
                }
                if (i < len) i++; // skip close quote
                map.put(key, sb.toString());
            } else if (c == '[') {
                i++;
                List<String> list = new ArrayList<>();
                while (i < len && json.charAt(i) != ']') {
                    while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
                    if (i >= len || json.charAt(i) == ']') break;
                    if (json.charAt(i) == '"') {
                        i++;
                        int start = i;
                        while (i < len && json.charAt(i) != '"') i++;
                        list.add(json.substring(start, i));
                        if (i < len) i++; // skip close quote
                    } else {
                        int start = i;
                        while (i < len && json.charAt(i) != ',' && json.charAt(i) != ']') i++;
                        list.add(json.substring(start, i).trim());
                    }
                }
                if (i < len && json.charAt(i) == ']') i++;
                map.put(key, list);
            } else {
                int valStart = i;
                while (i < len && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                String valStr = json.substring(valStart, i).trim();
                if ("true".equalsIgnoreCase(valStr)) map.put(key, true);
                else if ("false".equalsIgnoreCase(valStr)) map.put(key, false);
                else if ("null".equalsIgnoreCase(valStr)) map.put(key, null);
                else {
                    try {
                        if (valStr.contains(".")) {
                            map.put(key, Double.parseDouble(valStr));
                        } else {
                            map.put(key, Long.parseLong(valStr));
                        }
                    } catch (NumberFormatException nfe) {
                        map.put(key, valStr);
                    }
                }
            }
        }
        return map;
    }
}
