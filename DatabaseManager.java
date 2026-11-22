import java.sql.*;

/** Data Access Layer: all other managers go through this class. */
public class DatabaseManager {
    private String connectionString;
    private String databaseName;
    private boolean connectionStatus;
    private Connection connection;
    private String lastError = "";

    public DatabaseManager(String connectionString, String databaseName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.connectionStatus = false;
    }

    public boolean connectDatabase() {
        try {
            connection = DriverManager.getConnection(connectionString);
            connectionStatus = true;
            lastError = "";
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("Connection failed: " + e.getMessage()
                    + "  (is the sqlite-jdbc jar in the lib folder / project libraries?)");
            connectionStatus = false;
        }
        return connectionStatus;
    }

    public boolean runSql(String sql) {
        if (!connectionStatus) { lastError = "not connected"; return false; }
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
            return true;
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("SQL failed: " + e.getMessage() + " [SQL=" + sql + "]");
            return false;
        }
    }

    public boolean insertRecord(String sql, Object... params) { return execute(sql, params); }
    public boolean updateRecord(String sql, Object... params) { return execute(sql, params); }
    public boolean deleteRecord(String sql, Object... params) { return execute(sql, params); }

    public ResultSet retrieveRecord(String sql, Object... params) {
        if (!connectionStatus) { lastError = "not connected"; return null; }
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            bind(ps, params);
            return ps.executeQuery();
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("Query failed: " + e.getMessage() + " [SQL=" + sql + "]");
            return null;
        }
    }

    private boolean execute(String sql, Object... params) {
        if (!connectionStatus) { lastError = "not connected"; return false; }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("Statement failed: " + e.getMessage() + " [SQL=" + sql + "]");
            return false;
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    public String getDatabaseName() { return databaseName; }
    public boolean isConnected() { return connectionStatus; }
    public String getLastError() { return lastError; }
}