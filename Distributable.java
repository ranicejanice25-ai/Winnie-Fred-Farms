import java.util.List;

/**
 * Anything DocumentDistribution can send or export: an invoice or a receipt.
 */
public interface Distributable {

    /** The invoice number or receipt number. */
    int getDocumentId();

    /** Short document name used in the file name, PDF heading and email subject, e.g. "Invoice" or "Receipt". */
    String getTitle();

    /** The text lines to print in the PDF, one per line (customer name, quantity, unit cost, total and so on). */
    List<String> toDocumentLines();
}