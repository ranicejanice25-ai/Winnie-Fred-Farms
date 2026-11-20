import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Theme.apply(); // honey theme must be set before any window is created

        // Change the connection string to match your database/driver.
        DatabaseManager db = new DatabaseManager("jdbc:sqlite:winniefred.db", "winniefred");
        db.connectDatabase();
        AuthManager auth = new AuthManager(db);

        SwingUtilities.invokeLater(() -> new MainWindow(db, auth).openLoginWindow());
    }
}
