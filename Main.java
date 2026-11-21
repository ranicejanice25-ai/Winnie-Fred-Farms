import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Theme.apply(); // honey theme must be set before any window is created

        // The database file is created in the folder you run the program from.
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

    /** Creates the tables and the three sample logins if they don't exist yet (same as schema.sql). */
    private static void setupDatabase(DatabaseManager db) {
        db.runSql("CREATE TABLE IF NOT EXISTS users ("
                + "username TEXT PRIMARY KEY, password TEXT NOT NULL, role INTEGER NOT NULL)");
        db.runSql("CREATE TABLE IF NOT EXISTS inventory ("
                + "inventoryID INTEGER PRIMARY KEY, itemName TEXT NOT NULL, quantity INTEGER NOT NULL, "
                + "unitPrice REAL NOT NULL, dateUpdated DATE)");

        String hash = AuthManager.hash("admin123");
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "harvest", hash, 1);
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "orders", hash, 2);
        db.insertRecord("INSERT OR IGNORE INTO users VALUES (?,?,?)", "finance", hash, 3);
    }
}
