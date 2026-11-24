import java.sql.*;
import java.time.LocalDate;

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

    /**Reads a single int from the first column of the first
     * row, or -1 if none. */
    private int queryInt(String sql, Object... params){
        ResultSet rs = retrieveRecord(sql, params);
        if (rs == null) return -1;
        Statement st = null;
        try {
            st = rs.getStatement();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            lastError = e.getMessage();
            return -1;
        } finally {
            // retrieveRecord leaves the statement open, so close both here
            try { rs.close(); } catch (SQLException ignored) { }
            try { if (st != null) st.close(); } catch (SQLException ignored) { }
        }
    }

    /**
     * Returns the customer ID attached to a customer's email
     * @param email the email to be queried
     * @return the desired customer ID, or -1 otherwise
     */
    public int findCustomerIdByEmail(String email){
    if (email == null || email.isBlank()) return -1;
    return queryInt("SELECT customerID FROM customers WHERE email = ? COLLATE NOCASE",
                    email.trim());
    }

    /**
     * Inserts a customer into the database and returns the generated customer ID
     * @param name name of customer to be added to database
     * @param email email of customer to be added to database
     * @return the customer's assigned ID
     */
    public int insertCustomer(String name, String email){
        String cleanEmail = (email == null || email.isBlank()) ? null : email.trim();
        if (!insertRecord("INSERT INTO customers (name, email) VALUES (?, ?)", name, cleanEmail)){
            // If the unique index rejected a duplicate email, reuse the existing customer
            return findCustomerIdByEmail(cleanEmail);
        }
        return queryInt("SELECT last_insert_rowid()");
    }

    /**
     * Checks if a customer exists in the database utilisng customer ID as the primary key, adding them to the database if not
     * @param name name of customer to be checked for
     * @param email email of customer to be checked for
     * @return the ID of the customer
     */
    public int findOrCreateCustomer(String name, String email) {
        int id = findCustomerIdByEmail(email);
        return (id != -1) ? id : insertCustomer(name, email);
    }

    /**
     * Inserts a provided order into the database
     * @param o the order to be added to the database
     * @return the generarted orderID or -1
     */
    public int insertOrder(OrderRecord o) {
        boolean ok = insertRecord(
            "INSERT INTO orders (customerID, orderDate, quantity, unitCost, "
            + "totalAmount, balance, orderStatus) VALUES (?, ?, ?, ?, ?, ?, ?)",
            o.getCustomerID(), o.getDateCreated().toString(), o.getQuantityOrdered(),
            o.getUnitCost(), o.getTotalCost(), o.getRemainingBalance(), o.getOrderStatus());
        return ok ? queryInt("SELECT last_insert_rowid()") : -1;
    }

        /** Returns the quantity in stock for an item, or -1 if the item isn't in inventory. */
    public int getStock(String itemName) {
        return queryInt("SELECT quantity FROM inventory WHERE itemName = ? COLLATE NOCASE", itemName);
    }

    /** Subtracts qty from stock. Returns false (and changes nothing) if there isn't enough. */
    public boolean deductStock(String itemName, int qty) {
        return updateRecord(
            "UPDATE inventory SET quantity = quantity - ?, dateUpdated = ? "
        + "WHERE itemName = ? COLLATE NOCASE AND quantity >= ?",
            qty, LocalDate.now().toString(), itemName, qty);
    }

    /** Adds qty back to stock (used to undo a deduction if the order fails to save). */
    public boolean restoreStock(String itemName, int qty) {
        return updateRecord(
            "UPDATE inventory SET quantity = quantity + ?, dateUpdated = ? "
        + "WHERE itemName = ? COLLATE NOCASE",
            qty, LocalDate.now().toString(), itemName);
    }

    /** Returns the unit price for an item, or -1 if it isn't in inventory. */
    public double getUnitPrice(String itemName){
        ResultSet rs = retrieveRecord(
            "SELECT unitPrice FROM inventory WHERE itemName = ? COLLATE NOCASE", itemName);
        if (rs == null) return -1;
        Statement st = null;
        try{
            st = rs.getStatement();
            return rs.next() ? rs.getDouble(1) : -1;
        }catch (SQLException e){
            lastError = e.getMessage();
            return -1;
        }finally{
            try{
                rs.close();
            }catch(SQLException ignored){}
            try{
                if (st != null) st.close();
            }catch(SQLException ignored){}
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