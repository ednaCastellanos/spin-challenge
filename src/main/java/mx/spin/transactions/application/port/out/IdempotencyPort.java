package mx.spin.transactions.application.port.out;

import mx.spin.transactions.domain.model.TransactionId;

public interface IdempotencyPort {

    sealed interface Reservation {
        record Acquired()                        implements Reservation {}
        record InProgress()                      implements Reservation {}
        record AlreadyCompleted(TransactionId id) implements Reservation {}
    }

    Reservation reserve(String idempotencyKey);
    void complete(String idempotencyKey, TransactionId transactionId);
    void release(String idempotencyKey);
}