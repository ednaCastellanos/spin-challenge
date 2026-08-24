package mx.spin.transactions.domain.model;

import java.util.UUID;

public record TransactionId(UUID value) {
    public TransactionId {
        if (value == null) throw new IllegalArgumentException("transactionId is required");
    }
    public static TransactionId of(String raw) { return new TransactionId(UUID.fromString(raw)); }
    @Override public String toString() { return value.toString(); }
}