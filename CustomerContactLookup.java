/**
 * Finds the customer's email address for an invoice or a receipt.
 * DatabaseCustomerContactLookup is the real (database) version.
 */
public interface CustomerContactLookup {

    /** @return the email on file for this document, or null if there is none. */
    String findCustomerEmail(int documentID);
}