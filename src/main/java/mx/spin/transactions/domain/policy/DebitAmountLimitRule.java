package mx.spin.transactions.domain.policy;

import mx.spin.transactions.domain.model.Transaction;
import java.math.BigDecimal;
import java.util.Optional;

/** Regla 2: DEBIT no puede EXCEDER $10,000.00 (10,000.00 exacto se acepta). CREDIT sin límite. */
public final class DebitAmountLimitRule implements TransactionRule {

    public static final BigDecimal MAX_DEBIT = new BigDecimal("10000.00");

    @Override
    public Optional<RuleViolation> check(Transaction tx) {
        if (!tx.type().isDebit() || !tx.money().exceeds(MAX_DEBIT)) return Optional.empty();
        return Optional.of(new RuleViolation("DEBIT_LIMIT_EXCEEDED",
                "DEBIT transactions cannot exceed " + MAX_DEBIT));
    }
}