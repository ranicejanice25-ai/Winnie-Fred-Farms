import java.math.BigDecimal;

/**
 * This class holds the details of order records
 */

public class OrderRecord {

    private final String orderID;
    private final String customerName;
    private  BigDecimal remainingBalance;

    /**
     * Method creates an order with its ID, customer name and starting balance
     */

    public OrderRecord(String orderID, String customerName,
                       BigDecimal remainingBalance) {

        this.orderID = orderID;
        this.customerName = customerName;
        this.remainingBalance = remainingBalance;
    }

    /**
     * Returns Order ID
     */

    public String getOrderID() {
        return orderID;
    }

    /**
     * Returns customer name
     */

    public String getCustomerName() {
        return customerName;
    }

    /**
     * Returns amount owed
     */

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    /**
     * Replaces balance after payment is logged
     */

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }
}
