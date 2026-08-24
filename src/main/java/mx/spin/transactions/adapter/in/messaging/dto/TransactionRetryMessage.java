package mx.spin.transactions.adapter.in.messaging.dto;

import mx.spin.transactions.domain.event.TransactionRetryRequested;
import java.time.Instant;

/** DTO de transporte. El evento de dominio no se serializa directamente. */
public record TransactionRetryMessage(String transactionId, String accountId,
                                      int attempt, String cause, Instant occurredAt) {

    public static TransactionRetryMessage from(TransactionRetryRequested event) {
        return new TransactionRetryMessage(event.transactionId().toString(), event.accountId().value(),
                event.attempt(), event.cause(), event.occurredAt());
    }
}