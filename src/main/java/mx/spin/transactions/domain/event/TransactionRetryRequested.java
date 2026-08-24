package mx.spin.transactions.domain.event;

import mx.spin.transactions.domain.model.AccountId;
import mx.spin.transactions.domain.model.TransactionId;
import java.time.Instant;

/** Se publica cuando el proveedor falló TÉCNICAMENTE y la transacción sigue PENDING. */
public record TransactionRetryRequested(
        TransactionId transactionId, AccountId accountId, int attempt, String cause, Instant occurredAt) {

    public TransactionRetryRequested {
        if (transactionId == null || accountId == null) throw new IllegalArgumentException("ids are required");
        if (attempt < 0) throw new IllegalArgumentException("attempt cannot be negative");
    }

    public static TransactionRetryRequested from(mx.spin.transactions.domain.model.Transaction tx,
                                                 String cause, Instant now) {
        return new TransactionRetryRequested(tx.id(), tx.accountId(), tx.retryAttempts(), cause, now);
    }
}