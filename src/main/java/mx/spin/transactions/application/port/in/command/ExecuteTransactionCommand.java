package mx.spin.transactions.application.port.in.command;

import mx.spin.transactions.domain.model.TransactionType;
import java.math.BigDecimal;

public record ExecuteTransactionCommand(
        String accountId, TransactionType type, BigDecimal amount,
        String currency, String description, String idempotencyKey) {}