package mx.spin.transactions.application.service;

import mx.spin.transactions.application.port.in.ExecuteTransactionUseCase;
import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.application.port.out.IdempotencyPort;
import mx.spin.transactions.application.port.out.TransactionRepositoryPort;
import mx.spin.transactions.domain.exception.DuplicateRequestException;
import mx.spin.transactions.domain.exception.ProviderRejectedException;
import mx.spin.transactions.domain.exception.TransactionNotFoundException;
import mx.spin.transactions.domain.model.Transaction;
import mx.spin.transactions.domain.model.TransactionStatus;

/**
 * Decorator: añade idempotencia SIN modificar ExecuteTransactionService.
 * Si no viene Idempotency-Key, delega directamente.
 */
public class IdempotentExecuteTransactionDecorator implements ExecuteTransactionUseCase {

    private final ExecuteTransactionUseCase delegate;
    private final IdempotencyPort idempotency;
    private final TransactionRepositoryPort repository;

    public IdempotentExecuteTransactionDecorator(ExecuteTransactionUseCase delegate,
                                                 IdempotencyPort idempotency,
                                                 TransactionRepositoryPort repository) {
        this.delegate = delegate;
        this.idempotency = idempotency;
        this.repository = repository;
    }

    @Override
    public Transaction execute(ExecuteTransactionCommand command) {
        String key = command.idempotencyKey();
        if (key == null || key.isBlank()) return delegate.execute(command);

        return switch (idempotency.reserve(key)) {
            case IdempotencyPort.Reservation.InProgress ignored -> throw new DuplicateRequestException(key);
            case IdempotencyPort.Reservation.AlreadyCompleted completed -> replay(completed.id());
            case IdempotencyPort.Reservation.Acquired ignored -> executeAndRecord(command, key);
        };
    }

    private Transaction executeAndRecord(ExecuteTransactionCommand command, String key) {
        try {
            Transaction result = delegate.execute(command);
            idempotency.complete(key, result.id());     // EXECUTED o PENDING
            return result;

        } catch (ProviderRejectedException e) {
            // Rechazo de negocio: resultado TERMINAL. Se fija para que el replay
            // devuelva el mismo 422 y no vuelva a golpear al proveedor.
            idempotency.complete(key, e.transactionId());
            throw e;

        } catch (RuntimeException e) {
            // Violación de reglas o error inesperado: se libera para permitir
            // un reintento legítimo del cliente con la petición corregida.
            idempotency.release(key);
            throw e;
        }
    }

    /** Reproduce el resultado original sin volver a ejecutar nada. */
    private Transaction replay(mx.spin.transactions.domain.model.TransactionId id) {
        Transaction transaction = repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (transaction.status() == TransactionStatus.REJECTED) {
            throw new ProviderRejectedException(transaction.id(), transaction.failureReason());
        }
        return transaction;
    }
}