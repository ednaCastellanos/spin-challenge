package mx.spin.transactions.domain.model;

public record Currency(String code) {

    public static final Currency MXN = new Currency("MXN");

    public Currency {
        if (code == null || !code.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO-4217 code: " + code);
        }
    }

    public static Currency of(String raw) {
        return new Currency(raw == null ? null : raw.trim().toUpperCase());
    }
}