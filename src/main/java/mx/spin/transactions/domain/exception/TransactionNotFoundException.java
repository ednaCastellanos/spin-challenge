package mx.spin.transactions.domain.exception;

import mx.spin.transactions.domain.model.TransactionId;

public class TransactionNotFoundException extends DomainException {
    public TransactionNotFoundException(TransactionId id) { super("Transaction not found: " + id); }
    @Override public String code() { return "TRANSACTION_NOT_FOUND"; }
}