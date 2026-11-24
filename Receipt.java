import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
     * Numeric document ID required by Distributable
     */
    @Override
    public int getDocumentId() {
        return paymentID;
    }

    /**
     * Short document name required by Distributable
     */
    @Override
    public String getTitle() {
        return "Receipt";
    }

    /**
     * Lines printed into the exported PDF/document by DocumentDistribution
     */
    @Override
    public List<String> toDocumentLines() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy");
        return List.of(
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
                "=============================="
        );
    }

    /**
     * Builds printable receipt text for ReceiptGenerator
     */
    public String toText() {
        return String.join("\n", toDocumentLines());
    }

    public String getReceiptID() {
        return receiptID;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getOrderID() {
        return orderID;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public int getPaymentID() {
        return paymentID;
    }
}
