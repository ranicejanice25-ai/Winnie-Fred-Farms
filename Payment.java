import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * This class records one payment made against an order
 */

public class Payment {
    private final int paymentID;
    private final String orderID;
    private final BigDecimal amountPaid;
    private final String method;
    private final LocalDateTime paymentDate;

    /**
     * This method stores payment details with date and time
     */

    public Payment(int paymentID, String orderID, BigDecimal amountPaid, String method) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amountPaid = amountPaid;
        this.method = method;
        this.paymentDate = LocalDateTime.now();
    }

     /**
     * This method stores payment details for a payment that was saved earlier with the date comeing from the database
     */

    public Payment(int paymentID, String orderID, BigDecimal amountPaid, String method, LocalDateTime paymentDate) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amountPaid = amountPaid;
        this.method = method;
        this.paymentDate = paymentDate;
    }

    /**
     * Returns payment ID
     */

    public int getPaymentID() {
        return paymentID;
    }

    /**
     * Returns paid order ID
     */

    public String getOrderID() {

        return orderID;
    }

    /**
     * Returns amount paid
     */

    public BigDecimal getAmountPaid() {

        return amountPaid;
    }

    /**
     * Returns payment method
     */

    public String getMethod() {

        return method;
    }

    /**
     * Returns date when payment was logged
     */

    public LocalDateTime getPaymentDate() {

        return paymentDate;
    }

}
