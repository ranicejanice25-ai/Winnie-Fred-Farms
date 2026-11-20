-- Run once against your database (SQLite syntax shown).
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    password TEXT NOT NULL,      -- SHA-256 hex of the password
    role     INTEGER NOT NULL    -- 1 = harvest manager, 2 = order manager, 3 = finance manager
);

CREATE TABLE IF NOT EXISTS inventory (
    inventoryID INTEGER PRIMARY KEY,
    itemName    TEXT NOT NULL,
    quantity    INTEGER NOT NULL,
    unitPrice   REAL NOT NULL,
    dateUpdated DATE
);

-- Sample logins (password for all three: admin123)
INSERT OR IGNORE INTO users VALUES ('harvest', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 1);
INSERT OR IGNORE INTO users VALUES ('orders',  '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 2);
INSERT OR IGNORE INTO users VALUES ('finance', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 3);
