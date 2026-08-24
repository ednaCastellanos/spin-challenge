package mx.spin.transactions.config;

import mx.spin.transactions.application.port.in.ExecuteTransactionUseCase;
import mx.spin.transactions.application.port.in.SearchTransactionsUseCase;
import mx.spin.transactions.application.port.out.*;
import mx.spin.transactions.application.service.ExecuteTransactionService;
import mx.spin.transactions.application.service.IdempotentExecuteTransactionDecorator;
import mx.spin.transactions.application.service.RetryTransactionService;
import mx.spin.transactions.application.service.SearchTransactionsService;
import mx.spin.transactions.domain.model.TransactionId;
import mx.spin.transactions.domain.policy.TransactionRulesEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


import java.time.Clock;
import java.util.UUID;

@Configuration
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IdGeneratorPort idGenerator() {
        return () -> new TransactionId(UUID.randomUUID());
    }

    @Bean
    public TransactionRulesEngine transactionRulesEngine() {
        return TransactionRulesEngine.withDefaultRules();
    }

    /**
     * El controller recibe el decorador. ExecuteTransactionService no cambió ni una línea:
     * la idempotencia se compone por fuera (patrón Decorator).
     */
    @Bean
    @Primary
    public ExecuteTransactionUseCase idempotentExecuteTransactionUseCase(
            ExecuteTransactionUseCase executeTransactionUseCase,
            IdempotencyPort idempotency, TransactionRepositoryPort repository) {
        return new IdempotentExecuteTransactionDecorator(executeTransactionUseCase, idempotency, repository);
    }

    @Bean
    public RetryTransactionService retryTransactionService(
            TransactionRepositoryPort repository, PaymentProviderPort provider, Clock clock) {
        return new RetryTransactionService(repository, provider, clock);
    }

    @Bean
    public ExecuteTransactionUseCase executeTransactionUseCase(
            TransactionRulesEngine rulesEngine, TransactionRepositoryPort repository,
            PaymentProviderPort provider, TransactionEventPublisherPort eventPublisher,
            IdGeneratorPort idGenerator, Clock clock) {
        return new ExecuteTransactionService(rulesEngine, repository, provider, eventPublisher, idGenerator, clock);
    }

    @Bean
    public SearchTransactionsUseCase searchTransactionsUseCase(TransactionRepositoryPort repository) {
        return new SearchTransactionsService(repository);
    }
}