import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connects DocumentDistributor to your DatabaseManager. It finds the customer's email for an invoice or a receipt.
 *
 * The two default queries:
 *   invoices(invoiceID, orderID)   receipts(receiptID, orderID)
 *   orders(orderID, customerID)    customers(customerID, email)
 * 
 * The query must take one "?" (the document ID) and return the email address as its first column.
 */
public class DatabaseCustomerContactLookup implements CustomerContactLookup {

    public static final String INVOICE_SQL =
            "SELECT c.email FROM invoices i "
                    + "JOIN orders o ON o.orderID = i.orderID "
                    + "JOIN customers c ON c.customerID = o.customerID "
                    + "WHERE i.invoiceID = ?";

    public static final String RECEIPT_SQL =
            "SELECT c.email FROM receipts r "
                    + "JOIN orders o ON o.orderID = r.orderID "
                    + "JOIN customers c ON c.customerID = o.customerID "
                    + "WHERE r.receiptID = ?";

    private final DatabaseManager databaseManager;
    private final String sql;

    public DatabaseCustomerContactLookup(DatabaseManager databaseManager, String sql) {
        this.databaseManager = databaseManager;
        this.sql = sql;
    }

    public static DatabaseCustomerContactLookup forInvoices(DatabaseManager databaseManager) {
        return new DatabaseCustomerContactLookup(databaseManager, INVOICE_SQL);
    }

    public static DatabaseCustomerContactLookup forReceipts(DatabaseManager databaseManager) {
        return new DatabaseCustomerContactLookup(databaseManager, RECEIPT_SQL);
    }

    @Override
    public String findCustomerEmail(int documentID) {
        if (!databaseManager.isConnected()) {
            return null;
        }
        ResultSet rs = databaseManager.retrieveRecord(sql, documentID);
        if (rs == null) {
            return null;
        }
        Statement statement = null;
        try {
            statement = rs.getStatement();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            System.err.println("Could not read the customer email: " + e.getMessage());
            return null;
        } finally {
            // retrieveRecord leaves the statement open
            try { rs.close(); } catch (SQLException ignored) { }
            try { if (statement != null) statement.close(); } catch (SQLException ignored) { }
        }
    }
}