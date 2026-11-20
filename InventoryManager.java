import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    private final JTextField txtDate = new JTextField(LocalDate.now().toString(), 9);
    private final JButton cmdAdd = new Theme.HoneyButton("Add");
    private final JButton cmdUpdatePrice = new Theme.HoneyButton("Update Price");
    private final JButton cmdRemove = new Theme.HoneyButton("Remove");
    private final JButton cmdSortDate = new Theme.HoneyButton("Sort by Date");
    private final JButton cmdSortAmount = new Theme.HoneyButton("Sort by Amount");
    private final JButton cmdClose = new Theme.HoneyButton("Close");
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
        return addInventory(itemName, quantity, unitPrice, new Date());
    }

    /** Same as above, using the harvest/batch date entered by the user. */
    public boolean addInventory(String itemName, int quantity, float unitPrice, Date batchDate) {
        if (!db.isConnected()) return false;
        if (!isValidItem(itemName, quantity, unitPrice) || quantity == 0) return false;

        String name = itemName.trim();
        java.sql.Date day = new java.sql.Date(batchDate.getTime());

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
                    existing.getQuantity() + quantity, unitPrice, day, existing.getInventoryID());
        } else {
            ok = db.insertRecord(
                    "INSERT INTO inventory (inventoryID, itemName, quantity, unitPrice, dateUpdated) VALUES (?,?,?,?,?)",
                    maxId + 1, name, quantity, unitPrice, day);
        }
        if (ok) { this.itemName = name; this.quantity = quantity; this.unitPrice = unitPrice; this.dateUpdated = day; }
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

    /** Whole number greater than zero. Shows the matching error message and returns null if invalid. */
    private Integer parseQuantity(String text) {
        try {
            int q = Integer.parseInt(text);
            if (q < 0) { showError("Quantity cannot be negative."); return null; }
            if (q == 0) { showError("Quantity must be greater than zero."); return null; }
            return q;
        } catch (NumberFormatException ex) {
            try {
                Double.parseDouble(text);
                showError("Quantity must be a whole number of bottles.");
            } catch (NumberFormatException ex2) {
                showError("Quantity must be numeric.");
            }
            return null;
        }
    }

    /** Non-negative number. label is "Unit price" or "Unit cost" so the message matches the screen. */
    private Float parsePrice(String text, String label) {
        try {
            float p = Float.parseFloat(text);
            if (Float.isNaN(p) || Float.isInfinite(p)) { showError(label + " must be a valid number."); return null; }
            if (p < 0) { showError(label + " cannot be negative."); return null; }
            return p;
        } catch (NumberFormatException ex) {
            showError(label + " must be numeric.");
            return null;
        }
    }

    /** yyyy-MM-dd. Future dates are allowed (e.g. a planned harvest batch). */
    private Date parseDate(String text) {
        try {
            return java.sql.Date.valueOf(LocalDate.parse(text));
        } catch (DateTimeParseException ex) {
            showError("Date must be a valid date in the format yyyy-MM-dd (e.g. 2026-12-12).");
            return null;
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  GUI + listeners
    // =====================================================================

    private void buildGui() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        for (JTextField f : new JTextField[]{txtItem, txtQuantity, txtUnitPrice, txtDate}) Theme.styleField(f);
        Theme.styleTable(table);
        lblStatus.setFont(Theme.BODY);
        lblStatus.setForeground(Theme.BROWN);

        JPanel pnlFields = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        pnlFields.setOpaque(false);
        pnlFields.add(Theme.label("Item:"));                 pnlFields.add(txtItem);
        pnlFields.add(Theme.label("Quantity:"));             pnlFields.add(txtQuantity);
        pnlFields.add(Theme.label("Unit Price:"));           pnlFields.add(txtUnitPrice);
        pnlFields.add(Theme.label("Date (yyyy-MM-dd):"));    pnlFields.add(txtDate);

        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlActions.setOpaque(false);
        pnlActions.add(cmdAdd);
        pnlActions.add(cmdUpdatePrice);

        JPanel pnlInput = new JPanel(new BorderLayout());
        pnlInput.setOpaque(false);
        pnlInput.add(pnlFields, BorderLayout.NORTH);
        pnlInput.add(pnlActions, BorderLayout.CENTER);
        pnlInput.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setOpaque(false);
        pnlTop.add(Theme.header("Inventory Manager", "Honey stock and prices"), BorderLayout.NORTH);
        pnlTop.add(pnlInput, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Theme.CREAM);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlCommand.setOpaque(false);
        pnlCommand.add(cmdRemove);
        pnlCommand.add(cmdSortDate);
        pnlCommand.add(cmdSortAmount);
        pnlCommand.add(cmdClose);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.setOpaque(false);
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        pnlBottom.add(lblStatus, BorderLayout.WEST);
        pnlBottom.add(pnlCommand, BorderLayout.EAST);

        root.add(pnlTop, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(pnlBottom, BorderLayout.SOUTH);

        cmdAdd.addActionListener(new AddInventoryListener());
        cmdUpdatePrice.addActionListener(new UpdatePriceListener());
        cmdRemove.addActionListener(new RemoveInventoryListener());
        cmdSortDate.addActionListener(new SortDateListener());
        cmdSortAmount.addActionListener(new SortAmountListener());
        cmdClose.addActionListener(new CloseButtonListener());

        setSize(900, 500);
        setLocationRelativeTo(null);
    }

    /** Validates the form (TC-01.01 / 01.02 / 01.03) and adds the batch. */
    private class AddInventoryListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            String name = txtItem.getText().trim();
            String qtyText = txtQuantity.getText().trim();
            String priceText = txtUnitPrice.getText().trim();
            String dateText = txtDate.getText().trim();

            if (name.isEmpty() || qtyText.isEmpty() || priceText.isEmpty() || dateText.isEmpty()) {
                showError("Please fill in item, quantity, unit price and date.");
                return;
            }
            Integer qty = parseQuantity(qtyText);
            if (qty == null) return;
            Float price = parsePrice(priceText, "Unit price");
            if (price == null) return;
            Date batchDate = parseDate(dateText);
            if (batchDate == null) return;

            if (addInventory(name, qty, price, batchDate)) {
                txtItem.setText("");
                txtQuantity.setText("");
                txtUnitPrice.setText("");
                txtDate.setText(LocalDate.now().toString());
                refreshTable(retrieveInventory());
                lblStatus.setText("Saved " + qty + " x " + name + ".");
            } else {
                showError("Could not save the record. Check the database connection.");
                refreshTable(retrieveInventory());
            }
        }
    }

    /** Changes the unit cost of the selected record (TC-01.05 / 01.06). */
    private class UpdatePriceListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            int row = table.getSelectedRow();
            if (row < 0) { showError("Select a record to update."); return; }

            String priceText = txtUnitPrice.getText().trim();
            if (priceText.isEmpty()) { showError("Enter the new unit cost in the Unit Price box."); return; }
            Float price = parsePrice(priceText, "Unit cost");
            if (price == null) return; // invalid input: stored price stays unchanged

            int id = (Integer) model.getValueAt(row, 0);
            int qty = (Integer) model.getValueAt(row, 2);
            if (updateInventory(id, qty, price)) {
                txtUnitPrice.setText("");
                refreshTable(retrieveInventory());
                lblStatus.setText("Unit cost updated.");
            } else {
                showError("Could not update the unit cost. Check the database connection.");
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
