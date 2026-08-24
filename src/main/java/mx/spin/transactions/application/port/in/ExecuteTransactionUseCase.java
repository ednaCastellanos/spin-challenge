package mx.spin.transactions.application.port.in;

import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.domain.model.Transaction;

public interface ExecuteTransactionUseCase {
    Transaction execute(ExecuteTransactionCommand command);
}