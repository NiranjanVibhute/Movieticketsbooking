package com.movietickets.db;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class DatabaseConnector {
    private static final String DB_URL = "jdbc:sqlite:movietickets.db";
    private static DatabaseConnector instance;

    private DatabaseConnector() {
        try {
            Class.forName("org.sqlite.JDBC");
            initDatabase();
        } catch (Exception e) {
            System.err.println("Failed to initialize DatabaseConnector: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized DatabaseConnector getInstance() {
        if (instance == null) {
            instance = new DatabaseConnector();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        return conn;
    }

    private void initDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Read and execute schema.sql
            String schemaSql = loadResourceFile("/schema.sql");
            if (schemaSql != null && !schemaSql.isEmpty()) {
                String[] commands = schemaSql.split(";");
                for (String cmd : commands) {
                    if (!cmd.trim().isEmpty()) {
                        stmt.execute(cmd.trim());
                    }
                }
            } else {
                // Fallback table creation
                createTablesDirectly(stmt);
            }

            // Seed initial data if empty
            seedInitialData(conn);
            System.out.println("[DatabaseConnector] SQLite Database initialized successfully.");
        } catch (Exception e) {
            System.err.println("[DatabaseConnector] Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTablesDirectly(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS movies (id TEXT PRIMARY KEY, title TEXT NOT NULL, genre TEXT NOT NULL, duration INTEGER NOT NULL, rating TEXT NOT NULL, score REAL NOT NULL, poster_url TEXT NOT NULL, backdrop_url TEXT NOT NULL, synopsis TEXT NOT NULL, base_price REAL NOT NULL, is_blind_box INTEGER DEFAULT 0, blind_box_reveal_title TEXT, blind_box_discount_pct INTEGER DEFAULT 0, tags TEXT);");
        stmt.execute("CREATE TABLE IF NOT EXISTS seats (id TEXT PRIMARY KEY, movie_id TEXT NOT NULL, show_time TEXT NOT NULL, row_letter TEXT NOT NULL, seat_number INTEGER NOT NULL, tier TEXT NOT NULL, price REAL NOT NULL, is_booked INTEGER DEFAULT 0, vibe_tag TEXT DEFAULT 'NONE', FOREIGN KEY(movie_id) REFERENCES movies(id));");
        stmt.execute("CREATE TABLE IF NOT EXISTS bookings (id TEXT PRIMARY KEY, booking_ref TEXT UNIQUE NOT NULL, movie_id TEXT NOT NULL, show_time TEXT NOT NULL, seat_numbers TEXT NOT NULL, vibe_preference TEXT DEFAULT 'NONE', total_amount REAL NOT NULL, split_status TEXT DEFAULT 'NONE', split_code TEXT, customer_name TEXT NOT NULL, customer_email TEXT NOT NULL, snacks_json TEXT, booking_time DATETIME DEFAULT CURRENT_TIMESTAMP, delivery_status TEXT DEFAULT 'CONFIRMED', FOREIGN KEY(movie_id) REFERENCES movies(id));");
        stmt.execute("CREATE TABLE IF NOT EXISTS snacks (id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, base_price REAL NOT NULL, base_calories INTEGER NOT NULL, description TEXT, image_url TEXT);");
    }

    private void seedInitialData(Connection conn) {
        try {
            // Check if movies exist
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery("SELECT COUNT(*) FROM movies")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Already seeded
                }
            }

            System.out.println("[DatabaseConnector] Seeding initial movie and seat data...");

            // 1. Seed Movies
            String insertMovie = "INSERT INTO movies (id, title, genre, duration, rating, score, poster_url, backdrop_url, synopsis, base_price, is_blind_box, blind_box_reveal_title, blind_box_discount_pct, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertMovie)) {
                // Movie 1: Dune: Part Two
                ps.setString(1, "mov-dune2");
                ps.setString(2, "Dune: Part Two (IMAX 70mm)");
                ps.setString(3, "Sci-Fi / Epic / Adventure");
                ps.setInt(4, 166);
                ps.setString(5, "PG-13");
                ps.setDouble(6, 8.9);
                ps.setString(7, "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80");
                ps.setString(8, "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80");
                ps.setString(9, "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family in a desert planetary war.");
                ps.setDouble(10, 18.50);
                ps.setInt(11, 0);
                ps.setString(12, null);
                ps.setInt(13, 0);
                ps.setString(14, "IMAX 3D, Dolby Atmos, 4K Laser");
                ps.addBatch();

                // Movie 2: Cyberpunk: Neo Odyssey
                ps.setString(1, "mov-cyberpunk");
                ps.setString(2, "Cyberpunk: Neo Odyssey");
                ps.setString(3, "Action / Cyberpunk / Thriller");
                ps.setInt(4, 142);
                ps.setString(5, "R");
                ps.setDouble(6, 8.6);
                ps.setString(7, "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80");
                ps.setString(8, "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80");
                ps.setString(9, "In a rain-slicked mega-city ruled by rogue AI syndicates, a black-market netrunner discovers a quantum cipher that could reboot civilization.");
                ps.setDouble(10, 16.00);
                ps.setInt(11, 0);
                ps.setString(12, null);
                ps.setInt(13, 0);
                ps.setString(14, "Dolby Vision, Haptic Seats, 7.1 Surround");
                ps.addBatch();

                // Movie 3: Interstellar (Remastered)
                ps.setString(1, "mov-interstellar");
                ps.setString(2, "Interstellar: 10th Anniversary Remaster");
                ps.setString(3, "Sci-Fi / Drama / Space");
                ps.setInt(4, 169);
                ps.setString(5, "PG-13");
                ps.setDouble(6, 8.7);
                ps.setString(7, "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80");
                ps.setString(8, "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1200&auto=format&fit=crop&q=80");
                ps.setString(9, "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans.");
                ps.setDouble(10, 15.00);
                ps.setInt(11, 0);
                ps.setString(12, null);
                ps.setInt(13, 0);
                ps.setString(14, "IMAX Laser, Special 70mm Print, Director Cut");
                ps.addBatch();

                // Movie 4: Shadow of the Samurai
                ps.setString(1, "mov-samurai");
                ps.setString(2, "Shadow of the Shogun");
                ps.setString(3, "Action / Historical / Martial Arts");
                ps.setInt(4, 135);
                ps.setString(5, "R");
                ps.setDouble(6, 8.4);
                ps.setString(7, "https://images.unsplash.com/photo-1533558701576-23c65e0272fb?w=600&auto=format&fit=crop&q=80");
                ps.setString(8, "https://images.unsplash.com/photo-1528164344705-475426879c0d?w=1200&auto=format&fit=crop&q=80");
                ps.setString(9, "A lone wandering swordmaster defends an ancient mountain fortress against legions of supernatural invaders during feudal warfare.");
                ps.setDouble(10, 14.50);
                ps.setInt(11, 0);
                ps.setString(12, null);
                ps.setInt(13, 0);
                ps.setString(14, "High Dynamic Range, Atmos 3D");
                ps.addBatch();

                // Movie 5: SPECIAL BLIND BOX / MYSTERY MOVIE
                ps.setString(1, "mov-blindbox");
                ps.setString(2, "Mystery Movie: Blind Box Special");
                ps.setString(3, "Mystery / Thriller / Surprise Blockbuster");
                ps.setInt(4, 150);
                ps.setString(5, "PG-13 / R");
                ps.setDouble(6, 9.1);
                ps.setString(7, "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80");
                ps.setString(8, "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80");
                ps.setString(9, "Experience the ultimate cinema thrill! You get an unreleased pre-screening or award-winning masterpiece at a massive 40% discount. The exact movie identity unlocks upon ticket barcode confirmation!");
                ps.setDouble(10, 11.99); // Heavily discounted
                ps.setInt(11, 1); // is_blind_box = 1
                ps.setString(12, "Oppenheimer (Director's 70mm Secret Cut)");
                ps.setInt(13, 40); // 40% discount
                ps.setString(14, "Secret Screening, 40% OFF, Free Mystery Snack, Surprise Gift");
                ps.addBatch();

                ps.executeBatch();
            }

            // 2. Seed Seats for each movie and showtime
            seedSeats(conn);

            // 3. Seed Snacks
            seedSnacks(conn);

        } catch (Exception e) {
            System.err.println("[DatabaseConnector] Seed error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedSeats(Connection conn) throws SQLException {
        String[] movies = {"mov-dune2", "mov-cyberpunk", "mov-interstellar", "mov-samurai", "mov-blindbox"};
        String[] showTimes = {"18:30", "21:15"};
        char[] rows = {'A', 'B', 'C', 'D', 'E', 'F', 'G'};
        int seatsPerRow = 10;

        String insertSeat = "INSERT INTO seats (id, movie_id, show_time, row_letter, seat_number, tier, price, is_booked, vibe_tag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSeat)) {
            Random rand = new Random(42); // Deterministic seed for realism

            for (String movie : movies) {
                for (String time : showTimes) {
                    for (char row : rows) {
                        String tier = (row == 'A' || row == 'B') ? "VIP" : (row == 'C' || row == 'D' || row == 'E') ? "PREMIUM" : "STANDARD";
                        double basePrice = movie.equals("mov-blindbox") ? 11.99 : (tier.equals("VIP") ? 22.00 : tier.equals("PREMIUM") ? 17.50 : 13.00);

                        for (int s = 1; s <= seatsPerRow; s++) {
                            String seatId = String.format("%s_%s_%c%d", movie, time, row, s);
                            
                            // Deterministic pre-bookings for realism
                            boolean isBooked = false;
                            String vibeTag = "NONE";

                            // Simulate vibe sections
                            if (row == 'A' || row == 'B') {
                                vibeTag = (s <= 5) ? "QUIET" : "FAMILY";
                            } else if (row == 'C' || row == 'D') {
                                vibeTag = (s >= 4 && s <= 8) ? "EXCITED_FAN" : "QUIET";
                            } else {
                                vibeTag = (s <= 4) ? "FAMILY" : "EXCITED_FAN";
                            }

                            // Occasional booked seats
                            if ((row == 'C' && (s == 2 || s == 7)) || (row == 'D' && (s == 3 || s == 4)) || (row == 'A' && s == 5)) {
                                isBooked = true;
                            }

                            ps.setString(1, seatId);
                            ps.setString(2, movie);
                            ps.setString(3, time);
                            ps.setString(4, String.valueOf(row));
                            ps.setInt(5, s);
                            ps.setString(6, tier);
                            ps.setDouble(7, basePrice);
                            ps.setInt(8, isBooked ? 1 : 0);
                            ps.setString(9, vibeTag);
                            ps.addBatch();
                        }
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void seedSnacks(Connection conn) throws SQLException {
        String insertSnack = "INSERT INTO snacks (id, name, category, base_price, base_calories, description, image_url) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSnack)) {
            // Popcorn
            ps.setString(1, "snack-popcorn");
            ps.setString(2, "Cyber Truffle Popcorn");
            ps.setString(3, "POPCORN");
            ps.setDouble(4, 7.50);
            ps.setInt(5, 420);
            ps.setString(6, "Warm gourmet butterfly corn tossed in Himalayan pink salt and savory butter.");
            ps.setString(7, "https://images.unsplash.com/photo-1578849278619-e73505e9610f?w=400&auto=format&fit=crop&q=80");
            ps.addBatch();

            // Nachos
            ps.setString(1, "snack-nachos");
            ps.setString(2, "Volcanic Loaded Nachos");
            ps.setString(3, "NACHOS");
            ps.setDouble(4, 9.00);
            ps.setInt(5, 680);
            ps.setString(6, "Crispy artisanal tortilla chips paired with warm queso blanco and jalapeños.");
            ps.setString(7, "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?w=400&auto=format&fit=crop&q=80");
            ps.addBatch();

            // Soda / Drink
            ps.setString(1, "snack-beverage");
            ps.setString(2, "Quantum Neon Slush / Soda");
            ps.setString(3, "DRINKS");
            ps.setDouble(4, 5.00);
            ps.setInt(5, 180);
            ps.setString(6, "Chilled zero-sugar craft cola or electric blue raspberry frozen slush.");
            ps.setString(7, "https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=400&auto=format&fit=crop&q=80");
            ps.addBatch();

            // Hotdog
            ps.setString(1, "snack-hotdog");
            ps.setString(2, "Gourmet Angus Beef Dog");
            ps.setString(3, "COMBOS");
            ps.setDouble(4, 8.50);
            ps.setInt(5, 520);
            ps.setString(6, "Flame-grilled all-beef artisan frank in a toasted brioche bun with smoked aioli.");
            ps.setString(7, "https://images.unsplash.com/photo-1619740455993-9e612b1af08a?w=400&auto=format&fit=crop&q=80");
            ps.addBatch();

            ps.executeBatch();
        }
    }

    private String loadResourceFile(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
