package mx.spin.transactions.domain.policy;

import mx.spin.transactions.domain.model.Currency;
import mx.spin.transactions.domain.model.Transaction;
import java.util.Optional;

/** Regla 3: sólo MXN. */
public final class SupportedCurrencyRule implements TransactionRule {

    @Override
    public Optional<RuleViolation> check(Transaction tx) {
        return tx.money().isCurrency(Currency.MXN)
                ? Optional.empty()
                : Optional.of(new RuleViolation("UNSUPPORTED_CURRENCY",
                "Only MXN transactions are supported"));
    }
}