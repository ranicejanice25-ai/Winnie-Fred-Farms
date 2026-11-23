import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Invoice implements Distributable {
    private int invoiceID;
    private int orderID;
    private String customerName;
    private Date invoiceDate;
    private int quantity;
    private float unitCost;
    private float totalAmount;

    public Invoice(int invoiceID, int orderID, String customerName, Date invoiceDate, int quantity, float unitCost, float totalAmount) {
        this.invoiceID = invoiceID;
        this.orderID = orderID;
        this.customerName = customerName;
        this.invoiceDate = invoiceDate;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.totalAmount = totalAmount;
    }

    public int getInvoiceID() { return invoiceID; }
    public int getOrderID() { return orderID; }
    public String getCustomerName() { return customerName; }
    public Date getInvoiceDate() { return invoiceDate; }
    public int getQuantity() { return quantity; }
    public float getUnitCost() { return unitCost; }
    public float getTotalAmount() { return totalAmount; }

    @Override
    public int getDocumentId() { return invoiceID; }
    
    @Override
    public String getTitle() { return "Invoice"; }
    
    @Override
    public List<String> toDocumentLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Customer: " + customerName);
        lines.add("Order Reference: " + orderID);
        lines.add("Date: " + (invoiceDate != null ? invoiceDate : "-"));
        lines.add("--------------------------------------------------");
        lines.add("Description: Honey (Bottles)");
        lines.add("Quantity: " + quantity);
        lines.add("Unit Cost: $" + String.format("%.2f", unitCost));
        lines.add("--------------------------------------------------");
        lines.add("TOTAL: $" + String.format("%.2f", totalAmount));
        return lines;
    }
}