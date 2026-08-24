package mx.spin.transactions.architecture;

import mx.spin.transactions.domain.exception.BusinessRuleViolationException;
import mx.spin.transactions.domain.model.*;
import mx.spin.transactions.domain.policy.RuleViolation;
import mx.spin.transactions.domain.policy.TransactionRulesEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TransactionRulesEngineTest {

    private final TransactionRulesEngine engine = TransactionRulesEngine.withDefaultRules();

    private Transaction tx(String amount, String currency, TransactionType type) {
        return Transaction.pending(new TransactionId(UUID.randomUUID()), new AccountId("acc-1"),
                type, Money.of(new BigDecimal(amount), currency), "test", null, Instant.now());
    }

    @ParameterizedTest(name = "monto {0} -> válido={1}")
    @CsvSource({ "1.00,false", "1.01,true", "0.99,false", "1500.00,true" })
    @DisplayName("Regla 1: el monto debe ser estrictamente mayor a 1.00")
    void minimumAmountBoundary(String amount, boolean valid) {
        var transaction = tx(amount, "MXN", TransactionType.CREDIT);
        if (valid) engine.validate(transaction);
        else assertThatThrownBy(() -> engine.validate(transaction))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @ParameterizedTest(name = "DEBIT {0} -> válido={1}")
    @CsvSource({ "10000.00,true", "10000.01,false", "9999.99,true" })
    @DisplayName("Regla 2: DEBIT acepta exactamente 10,000.00 y rechaza por encima")
    void debitLimitBoundary(String amount, boolean valid) {
        var transaction = tx(amount, "MXN", TransactionType.DEBIT);
        if (valid) engine.validate(transaction);
        else assertThatThrownBy(() -> engine.validate(transaction))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("CREDIT no tiene límite superior")
    void creditHasNoUpperLimit() {
        engine.validate(tx("999999999.00", "MXN", TransactionType.CREDIT));
    }

    @Test
    @DisplayName("Se reportan TODAS las violaciones en una sola respuesta")
    void accumulatesViolations() {
        var transaction = tx("0.50", "USD", TransactionType.DEBIT);
        var ex = catchThrowableOfType(() -> engine.validate(transaction), BusinessRuleViolationException.class);
        assertThat(ex.violations()).extracting(RuleViolation::code)
                .containsExactlyInAnyOrder("AMOUNT_BELOW_MINIMUM", "UNSUPPORTED_CURRENCY");
    }
}