-- Winnie-Fred Farms Database Schema
-- Run this in a SQLite viewer or use it to recreate the database

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

CREATE TABLE IF NOT EXISTS customers (
    customerID INTEGER PRIMARY KEY,
    name       TEXT NOT NULL,
    email      TEXT
);

CREATE TABLE IF NOT EXISTS orders (
    orderID     INTEGER PRIMARY KEY,
    customerID  INTEGER NOT NULL,
    orderDate   DATE NOT NULL,
    orderStatus TEXT NOT NULL,
    totalAmount REAL NOT NULL,
    quantity    INTEGER,
    unitCost    REAL
);

CREATE TABLE IF NOT EXISTS invoices (
    invoiceID   INTEGER PRIMARY KEY,
    orderID     INTEGER NOT NULL,
    invoiceDate DATE NOT NULL,
    totalAmount REAL NOT NULL
);

-- Sample logins (password for all three: admin123)
INSERT OR IGNORE INTO users VALUES ('harvest', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 1);
INSERT OR IGNORE INTO users VALUES ('orders',  '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 2);
INSERT OR IGNORE INTO users VALUES ('finance', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 3);

-- Sample Data for testing Invoice Generation
INSERT OR IGNORE INTO customers VALUES (1, 'John Brown', 'john@example.com');
INSERT OR IGNORE INTO customers VALUES (2, '', 'missing@example.com'); 
INSERT OR IGNORE INTO customers VALUES (3, 'Anne Shirley', 'anne@example.com');
INSERT OR IGNORE INTO customers VALUES (4, 'Mary-Jane O''Connor', 'mary-jane@example.com');
INSERT OR IGNORE INTO customers VALUES (5, 'Lily Green', 'lily@example.com');
INSERT OR IGNORE INTO customers VALUES (6, 'Duke Walter', 'duke@example.com');
INSERT OR IGNORE INTO customers VALUES (7, 'Jason Jackson', 'jay@example.com');
INSERT OR IGNORE INTO customers VALUES (8, 'Wallace Steve', 'steavie@example.com');
INSERT OR IGNORE INTO customers VALUES (9, 'Alexandria Catherine Montgomery Kensington the 3rd', 'alex@example.com');

-- Orders matching the Test Schedule (TC-02 01 to 09)
INSERT OR IGNORE INTO orders VALUES (1, 1, '2026-11-10', 'COMPLETED', 10500.00, 3, 3500.00);
INSERT OR IGNORE INTO orders VALUES (2, 2, '2026-11-10', 'COMPLETED', 7000.00, 2, 3500.00);
INSERT OR IGNORE INTO orders VALUES (3, 3, '2026-11-10', 'COMPLETED', 17500.00, 5, 3500.00);
INSERT OR IGNORE INTO orders VALUES (4, 4, '2026-11-10', 'COMPLETED', 7000.00, 2, 3500.00);
INSERT OR IGNORE INTO orders VALUES (5, 5, '2026-11-10', 'COMPLETED', 3500.00, 1, 3500.00);
INSERT OR IGNORE INTO orders VALUES (6, 6, '2026-11-10', 'COMPLETED', 24000.00, 4, 4000.00);
INSERT OR IGNORE INTO orders VALUES (7, 7, '2026-11-10', 'COMPLETED', 7000.00, 2, 3500.00);
INSERT OR IGNORE INTO orders VALUES (8, 8, '2026-11-10', 'COMPLETED', 6001.31, 2, 3000.654);
INSERT OR IGNORE INTO orders VALUES (9, 9, '2026-11-10', 'COMPLETED', 3500.00, 1, 3500.00);