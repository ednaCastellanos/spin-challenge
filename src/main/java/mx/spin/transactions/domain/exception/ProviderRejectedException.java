package mx.spin.transactions.domain.exception;

import mx.spin.transactions.domain.model.FailureReason;
import mx.spin.transactions.domain.model.TransactionId;

/** El proveedor rechazó por NEGOCIO. Terminal: jamás se reintenta. */
public class ProviderRejectedException extends DomainException {

    private final FailureReason reason;
    private final TransactionId transactionId;

    public ProviderRejectedException(TransactionId transactionId, FailureReason reason) {
        super(reason.message());
        this.reason = reason;
        this.transactionId = transactionId;
    }

    public FailureReason reason() { return reason; }
    public TransactionId transactionId() { return transactionId; }
    @Override public String code() { return reason.code(); }
}