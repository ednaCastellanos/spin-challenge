package mx.spin.transactions.domain.model;

public enum TransactionType {
    CREDIT, DEBIT;
    public boolean isDebit() { return this == DEBIT; }
}