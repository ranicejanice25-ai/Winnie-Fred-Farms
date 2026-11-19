import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * InventoryManager (Winnie-Fred Farms) - window + logic for managing inventory.
 * Attributes, buttons/listeners and methods follow the class diagram and the Classes table.
 */
public class InventoryManager extends JFrame {

    // ---- attributes from the class diagram (current record) ----
    private int inventoryID;
    private String itemName;
    private int quantity;
    private float unitPrice;
    private Date dateUpdated;

    // ---- collaborators / state ----
    private final DatabaseManager db;
    private final List<InventoryItem> cache = new ArrayList<>(); // last good copy (offline view)
    private boolean usingCache = false;

    // ---- GUI components ----
    private final JTextField txtItem = new JTextField(12);
    private final JTextField txtQuantity = new JTextField(6);
    private final JTextField txtUnitPrice = new JTextField(6);
    private final JButton cmdAdd = new JButton("Add");
    private final JButton cmdRemove = new JButton("Remove");
    private final JButton cmdSortDate = new JButton("Sort by Date");
    private final JButton cmdSortAmount = new JButton("Sort by Amount");
    private final JButton cmdClose = new JButton("Close");
    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Item", "Quantity", "Unit Price", "Date Updated", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public InventoryManager(DatabaseManager db) {
        super("Winnie-Fred Farms - Inventory Manager");
        this.db = db;
        buildGui();
        refreshTable(retrieveInventory());
    }

    // =====================================================================
    //  Methods from the class diagram
    // =====================================================================

    /** Adds a new batch. If the item already exists, the quantity is added to the existing stock. */
    public boolean addInventory(String itemName, int quantity, float unitPrice) {
        if (!db.isConnected()) return false;
        if (!isValidItem(itemName, quantity, unitPrice) || quantity == 0) return false;

        String name = itemName.trim();
        java.sql.Date today = new java.sql.Date(new Date().getTime());

        InventoryItem existing = null;
        int maxId = 0;
        for (InventoryItem it : retrieveInventory()) {
            maxId = Math.max(maxId, it.getInventoryID());
            if (it.getItemName().equalsIgnoreCase(name)) existing = it;
        }

        boolean ok;
        if (existing != null) {
            ok = db.updateRecord(
                    "UPDATE inventory SET quantity = ?, unitPrice = ?, dateUpdated = ? WHERE inventoryID = ?",
                    existing.getQuantity() + quantity, unitPrice, today, existing.getInventoryID());
        } else {
            ok = db.insertRecord(
                    "INSERT INTO inventory (inventoryID, itemName, quantity, unitPrice, dateUpdated) VALUES (?,?,?,?,?)",
                    maxId + 1, name, quantity, unitPrice, today);
        }
        if (ok) { this.itemName = name; this.quantity = quantity; this.unitPrice = unitPrice; this.dateUpdated = today; }
        return ok;
    }

    /** Updates quantity and unit cost of an existing record (e.g. changing a category's price). */
    public boolean updateInventory(int inventoryID, int quantity, float unitPrice) {
        if (!db.isConnected() || !isValidItem("x", quantity, unitPrice)) return false;
        boolean ok = db.updateRecord(
                "UPDATE inventory SET quantity = ?, unitPrice = ?, dateUpdated = ? WHERE inventoryID = ?",
                quantity, unitPrice, new java.sql.Date(new Date().getTime()), inventoryID);
        if (ok) { this.inventoryID = inventoryID; this.quantity = quantity; this.unitPrice = unitPrice; }
        return ok;
    }

    public boolean deleteInventory(int inventoryID) {
        if (!db.isConnected()) return false;
        return db.deleteRecord("DELETE FROM inventory WHERE inventoryID = ?", inventoryID);
    }

    /**
     * Returns all inventory records. If the database is unreachable the last cached copy is
     * returned instead and usingCache is set, so the screen never crashes (TC-01.08).
     */
    public List<InventoryItem> retrieveInventory() {
        List<InventoryItem> items = new ArrayList<>();
        try (ResultSet rs = db.retrieveRecord(
                "SELECT inventoryID, itemName, quantity, unitPrice, dateUpdated FROM inventory")) {
            if (rs == null) {
                usingCache = true;
                return new ArrayList<>(cache);
            }
            while (rs.next()) {
                items.add(new InventoryItem(rs.getInt(1), rs.getString(2), rs.getInt(3),
                        rs.getFloat(4), rs.getDate(5)));
            }
        } catch (SQLException e) {
            usingCache = true;
            return new ArrayList<>(cache);
        }
        cache.clear();
        cache.addAll(items);
        usingCache = false;
        return items;
    }

    // =====================================================================
    //  Extra helpers
    // =====================================================================

    /** Called when an order is completed. Stock reaching 0 is shown as "Out of Stock" (TC-01.04). */
    public boolean deductStock(String itemName, int orderQuantity) {
        for (InventoryItem it : retrieveInventory()) {
            if (it.getItemName().equalsIgnoreCase(itemName)) {
                if (orderQuantity <= 0 || orderQuantity > it.getQuantity()) return false;
                return updateInventory(it.getInventoryID(), it.getQuantity() - orderQuantity, it.getUnitPrice());
            }
        }
        return false;
    }

    /** Valid = non-empty name, quantity >= 0, unit price >= 0 and a real number. */
    private boolean isValidItem(String name, int qty, float price) {
        return name != null && !name.trim().isEmpty() && qty >= 0
                && price >= 0 && !Float.isNaN(price) && !Float.isInfinite(price);
    }

    private void refreshTable(List<InventoryItem> items) {
        model.setRowCount(0);
        for (InventoryItem it : items) {
            model.addRow(new Object[]{
                    it.getInventoryID(), it.getItemName(), it.getQuantity(),
                    String.format("$%.2f", it.getUnitPrice()), it.getDateUpdated(),
                    it.isOutOfStock() ? "Out of Stock" : "In Stock"});
        }
        lblStatus.setText(usingCache
                ? "Database unavailable - showing last cached inventory."
                : items.size() + " record(s) loaded.");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  GUI + listeners
    // =====================================================================

    private void buildGui() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel pnlInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlInput.add(new JLabel("Item:"));       pnlInput.add(txtItem);
        pnlInput.add(new JLabel("Quantity:"));   pnlInput.add(txtQuantity);
        pnlInput.add(new JLabel("Unit Price:")); pnlInput.add(txtUnitPrice);
        pnlInput.add(cmdAdd);

        JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlCommand.add(cmdRemove);
        pnlCommand.add(cmdSortDate);
        pnlCommand.add(cmdSortAmount);
        pnlCommand.add(cmdClose);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.add(lblStatus, BorderLayout.WEST);
        pnlBottom.add(pnlCommand, BorderLayout.EAST);

        add(pnlInput, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(pnlBottom, BorderLayout.SOUTH);

        cmdAdd.addActionListener(new AddInventoryListener());
        cmdRemove.addActionListener(new RemoveInventoryListener());
        cmdSortDate.addActionListener(new SortDateListener());
        cmdSortAmount.addActionListener(new SortAmountListener());
        cmdClose.addActionListener(new CloseButtonListener());

        setSize(780, 420);
        setLocationRelativeTo(null);
    }

    private class AddInventoryListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            String name = txtItem.getText().trim();
            if (name.isEmpty() || txtQuantity.getText().trim().isEmpty() || txtUnitPrice.getText().trim().isEmpty()) {
                showError("Please fill in item, quantity and unit price.");
                return;
            }
            int qty;
            float price;
            try {
                qty = Integer.parseInt(txtQuantity.getText().trim());
                price = Float.parseFloat(txtUnitPrice.getText().trim());
            } catch (NumberFormatException ex) {
                showError("Quantity and unit price must be numeric.");
                return;
            }
            if (qty <= 0) { showError("Quantity must be greater than zero."); return; }
            if (price < 0) { showError("Unit price cannot be negative."); return; }

            if (addInventory(name, qty, price)) {
                txtItem.setText(""); txtQuantity.setText(""); txtUnitPrice.setText("");
                refreshTable(retrieveInventory());
            } else {
                showError("Could not save the record. Check the database connection.");
                refreshTable(retrieveInventory());
            }
        }
    }

    private class RemoveInventoryListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            int row = table.getSelectedRow();
            if (row < 0) { showError("Select a record to remove."); return; }
            int id = (Integer) model.getValueAt(row, 0);
            int choice = JOptionPane.showConfirmDialog(InventoryManager.this,
                    "Remove inventory record " + id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
            if (!deleteInventory(id)) showError("Could not remove the record. Check the database connection.");
            refreshTable(retrieveInventory());
        }
    }

    /** Shows records newest first. */
    private class SortDateListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            List<InventoryItem> items = retrieveInventory();
            items.sort(Comparator.comparing(InventoryItem::getDateUpdated,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            refreshTable(items);
        }
    }

    /** Shows records with the largest quantity first. */
    private class SortAmountListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            List<InventoryItem> items = retrieveInventory();
            items.sort(Comparator.comparingInt(InventoryItem::getQuantity).reversed());
            refreshTable(items);
        }
    }

    private class CloseButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { dispose(); }
    }

    // ---- getters for the diagram attributes ----
    public int getInventoryID() { return inventoryID; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public float getUnitPrice() { return unitPrice; }
    public Date getDateUpdated() { return dateUpdated; }
}
