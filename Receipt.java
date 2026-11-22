import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * This class contains everything that will appear on the payment receipt
 * Implements Distributable so receipt can be exported.
 */

public class Receipt implements Distributable {
    private final String receiptID;
    private final String companyName;
    private final String customerName;
    private final String orderID;
    private final BigDecimal amountPaid;
    private final String paymentMethod;
    private final BigDecimal remainingBalance;
    private final LocalDate paymentDate;
    private final int paymentID;

    /**
     * This method stores all the receipt values
     */

    public Receipt(String receiptID, String companyName, String customerName, int paymentID,
                   String orderID, BigDecimal amountPaid, String paymentMethod,
                   BigDecimal remainingBalance, LocalDate paymentDate) {

        this.receiptID = receiptID;
        this.companyName = companyName;
        this.customerName = customerName;
        this.orderID = orderID;
        this.amountPaid = amountPaid;
        this.paymentMethod = paymentMethod;
        this.remainingBalance = remainingBalance;
        this.paymentDate = paymentDate;
        this.paymentID = paymentID;

    }

    /**
     * Returns receipt ID
     */
    public String getReceiptID() {
        return receiptID;
    }

    /**
     *  Document ID is the receipt number.
     */

    @Override
    public String getDocumentID() {
        return receiptID;
    }

    /**
     * Returns company name
     */

    public String getCompanyName() {
        return companyName;
    }

    /**
     * Returns customer name
     */

    @Override
    public String getCustomerName() {
        return customerName;
    }



    /**
    public String getCustomerName() {
        return customerName;
    }
    */

    /**
     * Returns order ID
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Returns balance that is remaining
     */

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    /**
     * Returns payment date
     */

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    /**
     * Returns payment ID
     */

    public int getPaymentID() {
        return paymentID;
    }


    /**
     * Build's printable receipt text
     */

    @Override
    public String toText() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy");
        return String.join("\n",
                "==============================",
                "  " + companyName,
                "  PAYMENT RECEIPT",
                "==============================",
                "Receipt No : " + receiptID,
                "Date       : " + paymentDate.format(f),
                "Customer   : " + customerName,
                "Order ID   : " + orderID,
                "Method     : " + paymentMethod,
                "Amount Paid: JMD " + amountPaid,
                "Balance    : JMD " + remainingBalance,
                "==============================");
    }
}
