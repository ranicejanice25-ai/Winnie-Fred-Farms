import java.sql.*;

/** Data Access Layer: all other managers go through this class. */
public class DatabaseManager {
    private String connectionString;
    private String databaseName;
    private boolean connectionStatus;
    private Connection connection;

    public DatabaseManager(String connectionString, String databaseName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.connectionStatus = false;
    }

    public boolean connectDatabase() {
        try {
            connection = DriverManager.getConnection(connectionString);
            connectionStatus = true;
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            connectionStatus = false;
        }
        return connectionStatus;
    }

    public boolean insertRecord(String sql, Object... params) { return execute(sql, params); }
    public boolean updateRecord(String sql, Object... params) { return execute(sql, params); }
    public boolean deleteRecord(String sql, Object... params) { return execute(sql, params); }

    public ResultSet retrieveRecord(String sql, Object... params) {
        if (!connectionStatus) return null;
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            bind(ps, params);
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
            return null;
        }
    }

    private boolean execute(String sql, Object... params) {
        if (!connectionStatus) return false;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Statement failed: " + e.getMessage());
            return false;
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    public String getDatabaseName() { return databaseName; }
    public boolean isConnected() { return connectionStatus; }
}
