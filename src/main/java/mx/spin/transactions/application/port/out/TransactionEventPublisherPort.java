package mx.spin.transactions.application.port.out;

import mx.spin.transactions.domain.event.TransactionRetryRequested;

public interface TransactionEventPublisherPort {
    void publishRetryRequested(TransactionRetryRequested event);
}