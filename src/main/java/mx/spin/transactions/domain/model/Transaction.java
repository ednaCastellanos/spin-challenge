package mx.spin.transactions.domain.model;

import mx.spin.transactions.domain.exception.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {

    private final TransactionId id;
    private final AccountId accountId;
    private final TransactionType type;
    private final Money money;
    private final String description;
    private final String idempotencyKey;
    private final Instant createdAt;

    private TransactionStatus status;
    private String providerTransactionId;
    private BigDecimal balanceAfter;
    private FailureReason failureReason;
    private int retryAttempts;
    private Instant updatedAt;

    private Transaction(TransactionId id, AccountId accountId, TransactionType type, Money money,
                        String description, String idempotencyKey, TransactionStatus status,
                        String providerTransactionId, BigDecimal balanceAfter, FailureReason failureReason,
                        int retryAttempts, Instant createdAt, Instant updatedAt) {
        this.id = id; this.accountId = accountId; this.type = type; this.money = money;
        this.description = description; this.idempotencyKey = idempotencyKey; this.status = status;
        this.providerTransactionId = providerTransactionId; this.balanceAfter = balanceAfter;
        this.failureReason = failureReason; this.retryAttempts = retryAttempts;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    /** Nueva transacción aceptada localmente, aún no ejecutada contra el proveedor. */
    public static Transaction pending(TransactionId id, AccountId accountId, TransactionType type,
                                      Money money, String description, String idempotencyKey, Instant now) {
        if (id == null || accountId == null || type == null || money == null || now == null) {
            throw new IllegalArgumentException("missing required fields to create a transaction");
        }
        return new Transaction(id, accountId, type, money, description, idempotencyKey,
                TransactionStatus.PENDING, null, null, null, 0, now, now);
    }

    /** Rehidratación desde persistencia. Uso exclusivo del adaptador de salida. */
    public static Transaction rehydrate(TransactionId id, AccountId accountId, TransactionType type,
                                        Money money, String description, String idempotencyKey,
                                        TransactionStatus status, String providerTransactionId,
                                        BigDecimal balanceAfter, FailureReason failureReason,
                                        int retryAttempts, Instant createdAt, Instant updatedAt) {
        return new Transaction(id, accountId, type, money, description, idempotencyKey, status,
                providerTransactionId, balanceAfter, failureReason, retryAttempts, createdAt, updatedAt);
    }

    public void markExecuted(String providerTransactionId, BigDecimal balanceAfter, Instant now) {
        transitionTo(TransactionStatus.EXECUTED, now);
        this.providerTransactionId = providerTransactionId;
        this.balanceAfter = balanceAfter;
    }

    public void markRejected(FailureReason reason, Instant now) {
        transitionTo(TransactionStatus.REJECTED, now);
        this.failureReason = reason;
    }

    /** Reintentos agotados: fallo técnico definitivo. */
    public void markFailed(FailureReason reason, Instant now) {
        transitionTo(TransactionStatus.FAILED, now);
        this.failureReason = reason;
    }

    public void registerRetryAttempt(Instant now) {
        transitionTo(TransactionStatus.PENDING, now);
        this.retryAttempts++;
    }

    private void transitionTo(TransactionStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(id, status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public TransactionId id()               { return id; }
    public AccountId accountId()            { return accountId; }
    public TransactionType type()           { return type; }
    public Money money()                    { return money; }
    public String description()             { return description; }
    public String idempotencyKey()          { return idempotencyKey; }
    public TransactionStatus status()       { return status; }
    public String providerTransactionId()   { return providerTransactionId; }
    public BigDecimal balanceAfter()        { return balanceAfter; }
    public FailureReason failureReason()    { return failureReason; }
    public int retryAttempts()              { return retryAttempts; }
    public Instant createdAt()              { return createdAt; }
    public Instant updatedAt()              { return updatedAt; }
}