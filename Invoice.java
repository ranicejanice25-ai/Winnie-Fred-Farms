import java.util.Date;

public class Invoice {
    private final int invoiceID;
    private final int orderID;
    private final Date invoiceDate;
    private final float totalAmount;
    private final int quantity;
    private final float unitCost;
    private final String customerName;
    private final String orderStatus;
    private final Date orderDate;

    public Invoice(int invoiceID, int orderID, Date invoiceDate, float totalAmount, int quantity, float unitCost) {
        this(invoiceID, orderID, invoiceDate, totalAmount, quantity, unitCost, "", "", null);
    }

    public Invoice(int invoiceID, int orderID, Date invoiceDate, float totalAmount, int quantity, float unitCost,
                   String customerName, String orderStatus) {
        this(invoiceID, orderID, invoiceDate, totalAmount, quantity, unitCost, customerName, orderStatus, null);
    }

    public Invoice(int invoiceID, int orderID, Date invoiceDate, float totalAmount, int quantity, float unitCost,
                   String customerName, String orderStatus, Date orderDate) {
        this.invoiceID = invoiceID;
        this.orderID = orderID;
        this.invoiceDate = invoiceDate;
        this.totalAmount = totalAmount;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.customerName = customerName == null ? "" : customerName;
        this.orderStatus = orderStatus == null ? "" : orderStatus;
        this.orderDate = orderDate;
    }

    public int getInvoiceID() { return invoiceID; }
    public int getOrderID() { return orderID; }
    public Date getInvoiceDate() { return invoiceDate; }
    public float getTotalAmount() { return totalAmount; }
    public int getQuantity() { return quantity; }
    public float getUnitCost() { return unitCost; }
    public String getCustomerName() { return customerName; }
    public String getOrderStatus() { return orderStatus; }
    public Date getOrderDate() { return orderDate; }
}