package mx.spin.transactions.application.service;

import mx.spin.transactions.application.port.in.RetryTransactionUseCase;
import mx.spin.transactions.application.port.out.PaymentProviderPort;
import mx.spin.transactions.application.port.out.TransactionRepositoryPort;
import mx.spin.transactions.domain.exception.TransactionNotFoundException;
import mx.spin.transactions.domain.model.*;

import java.time.Clock;

public class RetryTransactionService implements RetryTransactionUseCase {

    private final TransactionRepositoryPort repository;
    private final PaymentProviderPort provider;
    private final Clock clock;

    public RetryTransactionService(TransactionRepositoryPort repository, PaymentProviderPort provider, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    @Override
    public void retry(TransactionId transactionId) {
        Transaction transaction = repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // Idempotente: un mensaje reentregado sobre una transacción ya resuelta no hace nada.
        if (transaction.status().isTerminal()) return;

        transaction.registerRetryAttempt(clock.instant());
        repository.save(transaction);

        switch (provider.execute(transaction)) {
            case ProviderResult.Approved approved -> {
                transaction.markExecuted(approved.providerTransactionId(), approved.balanceAfter(), clock.instant());
                repository.save(transaction);
            }
            case ProviderResult.Rejected rejected -> {
                transaction.markRejected(rejected.reason(), clock.instant());
                repository.save(transaction);
            }
        }
        // ProviderUnavailableException se propaga a propósito: es la señal
        // que hace que Kafka reintente en el siguiente topic.
    }

    /** Reintentos agotados: cierre definitivo desde la DLQ. */
    public void markAsFailed(TransactionId transactionId, String cause) {
        repository.findById(transactionId).ifPresent(transaction -> {
            if (transaction.status().isTerminal()) return;
            transaction.markFailed(FailureReason.of("RETRIES_EXHAUSTED", cause), clock.instant());
            repository.save(transaction);
        });
    }
}