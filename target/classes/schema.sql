-- Movie Ticket Booking SQLite Schema

CREATE TABLE IF NOT EXISTS movies (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    genre TEXT NOT NULL,
    duration INTEGER NOT NULL, -- minutes
    rating TEXT NOT NULL, -- PG-13, R, etc.
    score REAL NOT NULL, -- IMDb score e.g. 8.8
    poster_url TEXT NOT NULL,
    backdrop_url TEXT NOT NULL,
    synopsis TEXT NOT NULL,
    base_price REAL NOT NULL,
    is_blind_box INTEGER DEFAULT 0, -- 1 if Blind Box mystery movie
    blind_box_reveal_title TEXT,
    blind_box_discount_pct INTEGER DEFAULT 0,
    tags TEXT -- JSON or comma separated tags
);

CREATE TABLE IF NOT EXISTS seats (
    id TEXT PRIMARY KEY,
    movie_id TEXT NOT NULL,
    show_time TEXT NOT NULL,
    row_letter TEXT NOT NULL,
    seat_number INTEGER NOT NULL,
    tier TEXT NOT NULL, -- VIP, PREMIUM, STANDARD
    price REAL NOT NULL,
    is_booked INTEGER DEFAULT 0,
    vibe_tag TEXT DEFAULT 'NONE', -- QUIET, EXCITED_FAN, FAMILY, NONE
    FOREIGN KEY(movie_id) REFERENCES movies(id)
);

CREATE TABLE IF NOT EXISTS bookings (
    id TEXT PRIMARY KEY,
    booking_ref TEXT UNIQUE NOT NULL,
    movie_id TEXT NOT NULL,
    show_time TEXT NOT NULL,
    seat_numbers TEXT NOT NULL, -- Comma-separated: "A3, A4"
    vibe_preference TEXT DEFAULT 'NONE',
    total_amount REAL NOT NULL,
    split_status TEXT DEFAULT 'NONE', -- NONE, PENDING, COMPLETED
    split_code TEXT,
    customer_name TEXT NOT NULL,
    customer_email TEXT NOT NULL,
    snacks_json TEXT, -- JSON summary of ordered snacks
    booking_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    delivery_status TEXT DEFAULT 'CONFIRMED', -- CONFIRMED, PREPARING, IN_TRANSIT, DELIVERED
    FOREIGN KEY(movie_id) REFERENCES movies(id)
);

CREATE TABLE IF NOT EXISTS snacks (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL, -- POPCORN, NACHOS, DRINKS, COMBOS, SWEETS
    base_price REAL NOT NULL,
    base_calories INTEGER NOT NULL,
    description TEXT,
    image_url TEXT
);
