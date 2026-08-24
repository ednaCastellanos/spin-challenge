package mx.spin.transactions.domain.policy;

import mx.spin.transactions.domain.model.Transaction;
import java.math.BigDecimal;
import java.util.Optional;

/** Regla 1: el monto debe ser MAYOR a $1.00 (1.00 exacto se rechaza). */
public final class MinimumAmountRule implements TransactionRule {

    public static final BigDecimal MINIMUM = new BigDecimal("1.00");

    @Override
    public Optional<RuleViolation> check(Transaction tx) {
        return tx.money().isGreaterThan(MINIMUM)
                ? Optional.empty()
                : Optional.of(new RuleViolation("AMOUNT_BELOW_MINIMUM",
                "Transaction amount must be greater than " + MINIMUM));
    }
}