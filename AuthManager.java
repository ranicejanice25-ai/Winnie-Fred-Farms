import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Handles authentication and session state. Gets user data through DatabaseManager. */
public class AuthManager {
    private final DatabaseManager db;
    private User currentUser;
    private boolean sessionActive;

    public AuthManager(DatabaseManager db) { this.db = db; }

    /** Checks the username/password against the users table. Returns true if they match. */
    public boolean verifyUser(String username, String password) {
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) return false;
        try (ResultSet rs = db.retrieveRecord(
                "SELECT username, password, role FROM users WHERE username = ? AND password = ?",
                username, hash(password))) {
            if (rs != null && rs.next()) {
                currentUser = new User(rs.getString("username"), rs.getString("password"), rs.getInt("role"));
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Authentication error: " + e.getMessage());
        }
        currentUser = null;
        return false;
    }

    /** Returns the role of the verified user, or -1 if nobody is logged in. */
    public int verifyRole() { return currentUser == null ? -1 : currentUser.getRole(); }

    public void startSession() { if (currentUser != null) sessionActive = true; }

    public void endSession() { sessionActive = false; currentUser = null; }

    public boolean isDatabaseConnected() { return db.isConnected(); }

    public User getCurrentUser() { return currentUser; }
    public boolean isSessionActive() { return sessionActive; }

    /** SHA-256 hex digest - passwords are never stored or compared in plain text. */
    public static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
