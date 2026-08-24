package mx.spin.transactions.application.port.out;

import mx.spin.transactions.domain.model.TransactionId;

public interface IdGeneratorPort {
    TransactionId nextId();
}