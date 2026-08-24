package mx.spin.transactions.adapter.out.persistence;

import mx.spin.transactions.application.port.in.query.SearchTransactionsQuery;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

    private TransactionSpecifications() { }

    public static Specification<TransactionJpaEntity> from(SearchTransactionsQuery query) {
        return Specification.allOf(
                hasAccountId(query.accountId()),
                hasStatus(query.status() == null ? null : query.status().name()),
                hasType(query.type() == null ? null : query.type().name()));
    }

    private static Specification<TransactionJpaEntity> hasAccountId(String accountId) {
        return (root, q, cb) -> accountId == null || accountId.isBlank()
                                ? cb.conjunction() : cb.equal(root.get("accountId"), accountId);
    }

    private static Specification<TransactionJpaEntity> hasStatus(String status) {
        return (root, q, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    private static Specification<TransactionJpaEntity> hasType(String type) {
        return (root, q, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type);
    }
}