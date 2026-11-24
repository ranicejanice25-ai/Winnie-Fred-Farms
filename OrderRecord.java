import java.time.LocalDate;

/**
 * @author Danielle Murray
 * This class holds the details of an order record.
 */

public class OrderRecord{
    private int orderID;
    private final int customerID;
    private String customerName;
    private String customerEmail;
    private final int quantityOrdered;
    private final double unitCost;
    private double totalCost;
    private final LocalDate dateCreated;
    private double remainingBalance;
    private String orderStatus;

    /**
     * Method creates a new order with its customer ID, quantity of honey ordered,
     * the date the order was created, and the unit cost.
     */
    public OrderRecord(int customerID, LocalDate dateCreated, int quantityOrdered, double unitCost){
        this.customerID=customerID;
        this.quantityOrdered=quantityOrdered;
        this.unitCost=unitCost;
        this.totalCost=quantityOrdered*unitCost;
        this.dateCreated=dateCreated;
        this.remainingBalance=this.totalCost;
        this.setOrderStatus();
    }

    /**
     * Method that rebuilds an order from the database.
     */
    public OrderRecord(int orderID, int customerID, LocalDate dateCreated, String customerName, String customerEmail,
        int quantityOrdered, double unitCost, double totalCost, double remainingBalance, String orderStatus){
        this.orderID=orderID;
        this.customerID=customerID;
        this.dateCreated=dateCreated;
        this.customerName=customerName;
        this.customerEmail=customerEmail;
        this.quantityOrdered=quantityOrdered;
        this.unitCost=unitCost;
        this.totalCost=totalCost;
        this.remainingBalance=remainingBalance;
        this.orderStatus=orderStatus;
    }

    /**
     * Returns Order ID
     */
    public int getOrderID(){
        return orderID;
    }

    /**
     * Returns Customer ID
     */
    public int getCustomerID(){
        return customerID;
    }

    /**
     * Returns Customer name
     */
    public String getCustomerName(){
        return customerName;
    }

    /**
     * Returns Customer email address
     */
    public String getCustomerEmail(){
        return customerEmail;
    }

    /**
     * Returns the quantity of honey ordered
     */
    public int getQuantityOrdered(){
        return quantityOrdered;
    }

    /**
     * Returns the cost of a single unit of honey
     */
    public double getUnitCost(){
        return unitCost;
    }

    /**
     * Returns the total amount due for the quantity of honey ordered
     */
    public double getTotalCost(){
        return totalCost;
    }

    /**
     * Returns the date the order was created
     */
    public LocalDate getDateCreated(){
        return dateCreated;
    }

    /**
     * Returns the status of an order
     */
    public String getOrderStatus(){
        return orderStatus; 
    }

    /**
     * Returns amount owed
     */
    public double getRemainingBalance(){
        return remainingBalance;
    }
    
    /**
     * Sets the order ID
     * @param orderID the ID to be given to an order
     */
    public void setOrderID(int orderID){
        this.orderID=orderID;
    }

    /**
     * Sets the status of an order depending on if the customer still has
     * an outstanding balance.
     */
    public void setOrderStatus(){
        if (remainingBalance>0)
            orderStatus="INCOMPLETE";
        else
            orderStatus="COMPLETED";
    }

    /**
     * Replaces balance after payment is logged
     */
    public void setRemainingBalance(double remainingBalance) {
        this.remainingBalance = Math.max(0, remainingBalance);
        setOrderStatus();
    }
}
