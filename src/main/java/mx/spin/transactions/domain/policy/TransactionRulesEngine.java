package mx.spin.transactions.domain.policy;

import mx.spin.transactions.domain.exception.BusinessRuleViolationException;
import mx.spin.transactions.domain.model.Transaction;

import java.util.List;
import java.util.Optional;

/** Composite de reglas. Añadir una regla = añadir una clase (OCP: cero cambios aquí). */
public final class TransactionRulesEngine {

    private final List<TransactionRule> rules;

    public TransactionRulesEngine(List<TransactionRule> rules) {
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("at least one rule is required");
        this.rules = List.copyOf(rules);
    }

    public static TransactionRulesEngine withDefaultRules() {
        return new TransactionRulesEngine(List.of(
                new MinimumAmountRule(), new DebitAmountLimitRule(), new SupportedCurrencyRule()));
    }

    /** Evalúa TODAS las reglas y reporta el conjunto completo de violaciones. */
    public void validate(Transaction transaction) {
        List<RuleViolation> violations = rules.stream()
                .map(rule -> rule.check(transaction))
                .flatMap(Optional::stream)
                .toList();

        if (!violations.isEmpty()) throw new BusinessRuleViolationException(violations);
    }
}