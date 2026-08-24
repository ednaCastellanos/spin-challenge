package mx.spin.transactions.domain.model;

public record AccountId(String value) {
    public AccountId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("accountId is required");
        if (value.length() > 64) throw new IllegalArgumentException("accountId too long");
    }
}