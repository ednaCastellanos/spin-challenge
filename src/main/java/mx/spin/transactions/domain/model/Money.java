package mx.spin.transactions.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, Currency currency) {

    private static final int MINOR_UNIT_SCALE = 2;

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount is required");
        if (currency == null) throw new IllegalArgumentException("currency is required");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (amount.stripTrailingZeros().scale() > MINOR_UNIT_SCALE) {
            throw new IllegalArgumentException("amount supports at most 2 decimals: " + amount);
        }
        amount = amount.setScale(MINOR_UNIT_SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.of(currencyCode));
    }

    public boolean isGreaterThan(BigDecimal other) { return amount.compareTo(other) > 0; }
    public boolean exceeds(BigDecimal limit)       { return amount.compareTo(limit) > 0; }
    public boolean isCurrency(Currency other)      { return currency.equals(other); }
}