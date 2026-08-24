package mx.spin.transactions.domain.policy;

import mx.spin.transactions.domain.model.Transaction;
import java.util.Optional;

@FunctionalInterface
public interface TransactionRule {
    Optional<RuleViolation> check(Transaction transaction);
}