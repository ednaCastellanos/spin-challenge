package mx.spin.transactions.adapter.out.persistence.mapper;

import mx.spin.transactions.adapter.out.persistence.TransactionJpaEntity;
import mx.spin.transactions.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class TransactionPersistenceMapper {

    public TransactionJpaEntity toEntity(Transaction tx) {
        FailureReason reason = tx.failureReason();
        return new TransactionJpaEntity(
                tx.id().value(), tx.accountId().value(), tx.type().name(),
                tx.money().amount(), tx.money().currency().code(), tx.description(),
                tx.status().name(), tx.providerTransactionId(), tx.balanceAfter(),
                reason == null ? null : reason.code(),
                reason == null ? null : reason.message(),
                tx.idempotencyKey(), (short) tx.retryAttempts(), tx.createdAt(), tx.updatedAt());
    }

    public Transaction toDomain(TransactionJpaEntity e) {
        FailureReason reason = e.getFailureCode() == null
                ? null : FailureReason.of(e.getFailureCode(), e.getFailureMessage());

        return Transaction.rehydrate(
                new TransactionId(e.getId()), new AccountId(e.getAccountId()),
                TransactionType.valueOf(e.getType()),
                new Money(e.getAmount(), new Currency(e.getCurrency())),
                e.getDescription(), e.getIdempotencyKey(),
                TransactionStatus.valueOf(e.getStatus()), e.getProviderTransactionId(),
                e.getBalanceAfter(), reason, e.getRetryAttempts(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}