package sentinelgate.model;

import java.time.LocalDateTime;

public class BankTransaction extends Transaction {
    private String merchant;
    private String cardType;
    private int priorDeclineCount;

    public BankTransaction(String id, double amount, LocalDateTime timestamp, String location,
                           String accountId, String merchant, String cardType, int priorDeclineCount) {
        super(id, amount, timestamp, location, accountId);
        this.merchant = merchant;
        this.cardType = cardType;
        this.priorDeclineCount = priorDeclineCount;
    }

    // Getters
    public String getMerchant() { return merchant; }
    public String getCardType() { return cardType; }
    public int getPriorDeclineCount() { return priorDeclineCount; }

    @Override
    public String getTransactionDetails() {
        return String.format("Bank TX [%s]: $%.2f at %s using %s", getId(), getAmount(), merchant, cardType);
    }
}
