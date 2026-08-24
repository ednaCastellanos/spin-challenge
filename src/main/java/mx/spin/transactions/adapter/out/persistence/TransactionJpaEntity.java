package mx.spin.transactions.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", columnDefinition = "bpchar")
    private String currency;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "provider_transaction_id", length = 64)
    private String providerTransactionId;

    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "retry_attempts", nullable = false)
    private short retryAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionJpaEntity() { }   // requerido por JPA

    public TransactionJpaEntity(UUID id, String accountId, String type, BigDecimal amount, String currency,
                                String description, String status, String providerTransactionId,
                                BigDecimal balanceAfter, String failureCode, String failureMessage,
                                String idempotencyKey, short retryAttempts, Instant createdAt, Instant updatedAt) {
        this.id = id; this.accountId = accountId; this.type = type; this.amount = amount;
        this.currency = currency; this.description = description; this.status = status;
        this.providerTransactionId = providerTransactionId; this.balanceAfter = balanceAfter;
        this.failureCode = failureCode; this.failureMessage = failureMessage;
        this.idempotencyKey = idempotencyKey; this.retryAttempts = retryAttempts;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public short getRetryAttempts() { return retryAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}