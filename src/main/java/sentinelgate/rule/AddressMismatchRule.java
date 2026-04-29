package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.EcommerceOrder;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

public class AddressMismatchRule implements FraudRule {

    @Override
    public FraudFlag evaluate(Transaction t, Account a) {
        // This rule only applies to E-commerce transactions
        if (t instanceof EcommerceOrder) {
            EcommerceOrder order = (EcommerceOrder) t;

            String billing = order.getBillingAddress();
            String shipping = order.getShippingAddress();

            // If addresses are provided and they do not match...
            if (billing != null && shipping != null && !billing.equalsIgnoreCase(shipping)) {
                String context = String.format("Shipping address '%s' does not match the billing address '%s'. Guest checkout status: %b.",
                        shipping, billing, order.isGuestCheckout());

                // Elevated severity if they are also using guest checkout
                String severity = order.isGuestCheckout() ? "HIGH" : "MEDIUM";

                return new FraudFlag("Billing/Shipping Mismatch", severity, context);
            }
        }
        return null;
    }
}
