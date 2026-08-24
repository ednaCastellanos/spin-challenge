package mx.spin.transactions.domain.model;

public record FailureReason(String code, String message) {
    public FailureReason {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("failure code is required");
    }
    public static FailureReason of(String code, String message) { return new FailureReason(code, message); }
}