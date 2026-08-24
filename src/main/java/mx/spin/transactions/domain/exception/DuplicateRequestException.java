package mx.spin.transactions.domain.exception;

public class DuplicateRequestException extends DomainException {

    public DuplicateRequestException(String idempotencyKey) {
        super("A request with idempotency key '" + idempotencyKey + "' is already in progress");
    }

    @Override public String code() { return "DUPLICATE_REQUEST"; }
}