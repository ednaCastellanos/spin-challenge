package mx.spin.transactions.application.service;

import mx.spin.transactions.application.port.in.ExecuteTransactionUseCase;
import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.application.port.out.*;
import mx.spin.transactions.domain.event.TransactionRetryRequested;
import mx.spin.transactions.domain.exception.ProviderRejectedException;
import mx.spin.transactions.domain.exception.ProviderUnavailableException;
import mx.spin.transactions.domain.model.*;
import mx.spin.transactions.domain.policy.TransactionRulesEngine;

import java.time.Clock;
import java.time.Instant;

public class ExecuteTransactionService implements ExecuteTransactionUseCase {

    private final TransactionRulesEngine rulesEngine;
    private final TransactionRepositoryPort repository;
    private final PaymentProviderPort provider;
    private final TransactionEventPublisherPort eventPublisher;
    private final IdGeneratorPort idGenerator;
    private final Clock clock;

    public ExecuteTransactionService(TransactionRulesEngine rulesEngine, TransactionRepositoryPort repository,
                                     PaymentProviderPort provider, TransactionEventPublisherPort eventPublisher,
                                     IdGeneratorPort idGenerator, Clock clock) {
        this.rulesEngine = rulesEngine;
        this.repository = repository;
        this.provider = provider;
        this.eventPublisher = eventPublisher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public Transaction execute(ExecuteTransactionCommand command) {
        Transaction transaction = buildPending(command);

        // 1. Reglas ANTES del proveedor. Si fallan, no se persiste ni se ejecuta nada.
        rulesEngine.validate(transaction);

        // 2. Se persiste PENDING antes de la llamada externa: si el proceso muere
        //    a mitad del HTTP, queda rastro auditable en lugar de un hueco.
        Transaction pending = repository.save(transaction);

        try {
            return switch (provider.execute(pending)) {
                case ProviderResult.Approved approved -> {
                    pending.markExecuted(approved.providerTransactionId(), approved.balanceAfter(), clock.instant());
                    yield repository.save(pending);
                }
                case ProviderResult.Rejected rejected -> {
                    pending.markRejected(rejected.reason(), clock.instant());
                    repository.save(pending);
                    throw new ProviderRejectedException(pending.id(), rejected.reason());
                }
            };
        } catch (ProviderUnavailableException e) {
            // Fallo TÉCNICO: la transacción sigue PENDING y se delega al flujo de reintentos.
            eventPublisher.publishRetryRequested(
                    TransactionRetryRequested.from(pending, e.code(), clock.instant()));
            return pending;
        }
    }

    private Transaction buildPending(ExecuteTransactionCommand command) {
        Instant now = clock.instant();
        return Transaction.pending(
                idGenerator.nextId(),
                new AccountId(command.accountId()),
                command.type(),
                Money.of(command.amount(), command.currency()),
                command.description(),
                command.idempotencyKey(),
                now);
    }
}