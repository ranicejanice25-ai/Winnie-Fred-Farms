import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;

public class Main { 
    public static void main(String[] args) {
        Theme.apply();

        DatabaseManager db = new DatabaseManager("jdbc:sqlite:winniefred.db", "winniefred");
        if (!db.connectDatabase()) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to the database.\n\n"
                  + "Make sure the SQLite driver (lib/sqlite-jdbc-3.36.0.3.jar) is added to the\n"
                  + "project's libraries / classpath, then run again.",
                    "Database error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        setupDatabase(db);
        System.out.println("Using database: " + new File("winniefred.db").getAbsolutePath());

        AuthManager auth = new AuthManager(db);
        SwingUtilities.invokeLater(() -> new MainWindow(db, auth).openLoginWindow());
    }

    private static void setupDatabase(DatabaseManager db) {
        db.runSql("CREATE TABLE IF NOT EXISTS users ("
                + "username TEXT PRIMARY KEY, password TEXT NOT NULL, role INTEGER NOT NULL)");
        db.runSql("CREATE TABLE IF NOT EXISTS inventory ("
                + "inventoryID INTEGER PRIMARY KEY, itemName TEXT NOT NULL, quantity INTEGER NOT NULL, "
                + "unitPrice REAL NOT NULL, dateUpdated DATE)");

        db.runSql("CREATE TABLE IF NOT EXISTS customers ("
                + "customerID INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT)");
        db.runSql("CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email "
                + "ON customers(email COLLATE NOCASE)");
                
        db.runSql("CREATE TABLE IF NOT EXISTS orders ("
                + "orderID INTEGER PRIMARY KEY, customerID INTEGER NOT NULL, orderDate DATE NOT NULL, "
                + "quantity INTEGER, unitCost REAL, totalAmount REAL NOT NULL, balance REAL, "
                + "orderStatus TEXT NOT NULL)");
                
        db.runSql("CREATE TABLE IF NOT EXISTS invoices ("
                + "invoiceID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, "
                + "invoiceDate DATE NOT NULL, totalAmount REAL NOT NULL)");
                
        // Payments logged against orders (used by SalesReport for "amount collected")
        db.runSql("CREATE TABLE IF NOT EXISTS payments ("
                + "paymentID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, amountPaid REAL NOT NULL, "
                + "method TEXT, paymentDate DATE NOT NULL)");

        String hash = AuthManager.hash("admin123");
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "harvest", hash, 1);
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "orders", hash, 2);
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "finance", hash, 3);

        // --- NEW: Seed the inventory table with Honey and a default price ---
        db.insertRecord("INSERT OR IGNORE INTO inventory VALUES (?,?,?,?,?)", 1, "Honey", 30, 3500.00, "2026-11-10");

        // Sample Data for testing Invoice Generation
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 1, "Jamir Johnson", "jamir.johnson@mymona.uwi.edu");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 2, "", "missing@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 3, "Anne Shirley", "anne@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 4, "Mary-Jane O'Connor", "mary-jane@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 5, "Lily Green", "lily@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 6, "Maya Ellis", "maya.ellis@mymona.uwi.edu");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 7, "Jason Jackson", "jay@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 8, "Wallace Steve", "steavie@example.com");
        db.insertRecord("INSERT OR IGNORE INTO customers VALUES (?,?,?)", 9, "Alexandria Catherine Montgomery Kensington the 3rd", "alex@example.com");
 
      

        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 1, 1, "2026-11-10", 3, 3500.00, 10500.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 2, 2, "2026-11-10", 2, 3500.00, 7000.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 3, 3, "2026-11-10", 5, 3500.00, 17500.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 4, 4, "2026-11-10", 2, 3500.00, 7000.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 5, 5, "2026-11-10", 1, 3500.00, 3500.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 6, 6, "2026-11-10", 4, 4000.00, 24000.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 7, 7, "2026-11-10", 2, 3500.00, 7000.00, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 8, 8, "2026-11-10", 2, 1000.654, 2001.31, 0, "COMPLETED");
        db.insertRecord("INSERT OR IGNORE INTO orders VALUES (?,?,?,?,?,?,?,?)", 9, 9, "2026-11-10", 1, 3500.00, 3500.00, 0, "COMPLETED");
    }
}