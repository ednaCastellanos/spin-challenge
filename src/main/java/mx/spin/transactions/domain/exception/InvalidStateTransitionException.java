package mx.spin.transactions.domain.exception;

import mx.spin.transactions.domain.model.TransactionId;
import mx.spin.transactions.domain.model.TransactionStatus;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(TransactionId id, TransactionStatus from, TransactionStatus to) {
        super("Transaction %s cannot transition from %s to %s".formatted(id, from, to));
    }

    @Override public String code() { return "INVALID_STATE_TRANSITION"; }
}