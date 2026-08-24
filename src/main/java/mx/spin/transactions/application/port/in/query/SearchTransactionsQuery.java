package mx.spin.transactions.application.port.in.query;

import mx.spin.transactions.domain.model.TransactionStatus;
import mx.spin.transactions.domain.model.TransactionType;

public record SearchTransactionsQuery(
        String accountId, TransactionStatus status, TransactionType type, int page, int size) {

    public static final int MAX_SIZE = 100;

    public SearchTransactionsQuery {
        if (page < 0) throw new IllegalArgumentException("page cannot be negative");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
    }
}