package mx.spin.transactions.application.port.in;

import mx.spin.transactions.domain.model.TransactionId;

public interface RetryTransactionUseCase {
    /** Reintento disparado por Kafka. Idempotente: si ya es terminal, no hace nada. */
    void retry(TransactionId transactionId);
}