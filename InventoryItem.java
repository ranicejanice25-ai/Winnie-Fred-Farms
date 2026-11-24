import java.time.LocalDate;

/** One row of the inventory table. */
public class InventoryItem {
    private final int inventoryID;
    private final String itemName;      // product category, e.g. "Honey"
    private final int quantity;         // bottles in stock
    private final float unitPrice;
    private final LocalDate dateUpdated;

    public InventoryItem(int inventoryID, String itemName, int quantity, float unitPrice, LocalDate dateUpdated) {
        this.inventoryID = inventoryID;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.dateUpdated = dateUpdated;
    }

    public int getInventoryID() { return inventoryID; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public float getUnitPrice() { return unitPrice; }
    public LocalDate getDateUpdated() { return dateUpdated; }
    public boolean isOutOfStock() { return quantity == 0; }
}
