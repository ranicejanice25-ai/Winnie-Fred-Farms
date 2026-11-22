import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/**
 * Logs payments and generate receipts
  */

public class ReceiptGenerator {

    private static final String COMPANY = "Winnie-Fred Farms";
    private int nextPaymentID = 1;

    private final List<Payment> payments = new ArrayList<>();

    /**
     * Logs payment against an order. It checks that the method and amount are valid,
     *  subtracts the difference and create and saves payment.
     */


    public Payment logPayment(OrderRecord order, String amountEntered, String method) {
        if (method == null || method.isBlank())
           throw new IllegalArgumentException("Payment method is required.");
        BigDecimal amountPaid;

        /**
         * Converts text into number with 2 decimal places and rejects it if it is not a number
         */

        try {
            amountPaid = new BigDecimal(amountEntered.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("Payment amount is not valid. It must be a number.");
        }

        /**
         * This rejects all negative numbers and 0 as well as amounts bigger than amount owed
         */

        if (amountPaid.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be greater than 0.");
        if (amountPaid.compareTo(order.getRemainingBalance()) > 0)
            throw new IllegalArgumentException("Payment exceeds outstanding balance.");

        /**
         * Reduces balance, returns and records payment once everything is valid
         */

        order.setRemainingBalance(order.getRemainingBalance().subtract(amountPaid));
        Payment payment = new Payment(nextPaymentID++, order.getOrderID(), amountPaid, method);
        payments.add(payment);
        return payment;
    }


    /**
     * "WFF-": receipt number prefix
     */

    /**
     * Builds receipt from payment
     */
    public Receipt generateReceipt(Payment payment, OrderRecord order) {
        return new Receipt("WFF-" + String.format("%05d", payment.getPaymentID()),
                COMPANY, order.getCustomerName(), payment.getPaymentID(), order.getOrderID(),
                payment.getAmountPaid(), payment.getMethod(), order.getRemainingBalance(),
                payment.getPaymentDate().toLocalDate());
    }

    /**
     * Finds a payment by its ID and returns null if there is no payment
     *
     */

    public Payment retrievePaymentDetails(int paymentID) {
        for (Payment p : payments) {
            if (p.getPaymentID() == paymentID) return p;
        }
        return null;
    }

    /**
     * Printable text that shows receipt info
     *
     */

    public String formatAsText(Receipt r) {
        return r.toText();
    }
}
