package sentinelgate.model;

import java.time.LocalDateTime;

public class EcommerceOrder extends Transaction {
    private String billingAddress;
    private String shippingAddress;
    private boolean isGuestCheckout;

    public EcommerceOrder(String id, double amount, LocalDateTime timestamp, String location,
                          String accountId, String billingAddress, String shippingAddress, boolean isGuestCheckout) {
        super(id, amount, timestamp, location, accountId);
        this.billingAddress = billingAddress;
        this.shippingAddress = shippingAddress;
        this.isGuestCheckout = isGuestCheckout;
    }

    // Getters
    public String getBillingAddress() { return billingAddress; }
    public String getShippingAddress() { return shippingAddress; }
    public boolean isGuestCheckout() { return isGuestCheckout; }

    @Override
    public String getTransactionDetails() {
        return String.format("E-Commerce TX [%s]: $%.2f. Shipping to: %s. Guest: %b",
                getId(), getAmount(), shippingAddress, isGuestCheckout);
    }
}
