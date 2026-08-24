package mx.spin.transactions.application.service;

import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.application.port.out.*;
import mx.spin.transactions.domain.exception.BusinessRuleViolationException;
import mx.spin.transactions.domain.exception.ProviderRejectedException;
import mx.spin.transactions.domain.exception.ProviderUnavailableException;
import mx.spin.transactions.domain.model.*;
import mx.spin.transactions.domain.policy.TransactionRulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecuteTransactionServiceTest {

    @Mock TransactionRepositoryPort repository;
    @Mock PaymentProviderPort provider;
    @Mock TransactionEventPublisherPort eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2025-03-15T10:30:00Z"), ZoneOffset.UTC);
    private ExecuteTransactionService service;

    @BeforeEach
    void setUp() {
        IdGeneratorPort idGenerator = () -> new TransactionId(UUID.randomUUID());
        service = new ExecuteTransactionService(TransactionRulesEngine.withDefaultRules(),
                repository, provider, eventPublisher, idGenerator, clock);
    }

    private ExecuteTransactionCommand command(String amount, TransactionType type) {
        return new ExecuteTransactionCommand("acc-1", type, new BigDecimal(amount), "MXN", "test", null);
    }

    @Test
    @DisplayName("APPROVED: persiste EXECUTED con providerTransactionId y balanceAfter")
    void approvedFlow() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(provider.execute(any())).thenReturn(
                new ProviderResult.Approved("txn-789", new BigDecimal("5500.00"), clock.instant()));

        Transaction result = service.execute(command("1500.00", TransactionType.CREDIT));

        assertThat(result.status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(result.providerTransactionId()).isEqualTo("txn-789");
        assertThat(result.balanceAfter()).isEqualByComparingTo("5500.00");
        verify(repository, times(2)).save(any());   // PENDING y luego EXECUTED
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("REJECTED: persiste el rechazo y lanza ProviderRejectedException; no publica reintento")
    void rejectedFlow() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(provider.execute(any())).thenReturn(
                new ProviderResult.Rejected(FailureReason.of("INSUFFICIENT_FUNDS", "no funds")));

        assertThatThrownBy(() -> service.execute(command("500.00", TransactionType.DEBIT)))
                .isInstanceOf(ProviderRejectedException.class)
                .extracting(e -> ((ProviderRejectedException) e).reason().code())
                .isEqualTo("INSUFFICIENT_FUNDS");

        verifyNoInteractions(eventPublisher);   // un rechazo de negocio NUNCA se reintenta
    }

    @Test
    @DisplayName("Fallo técnico: queda PENDING y publica evento de reintento")
    void unavailableFlow() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(provider.execute(any())).thenThrow(new ProviderUnavailableException("timeout"));

        Transaction result = service.execute(command("100.00", TransactionType.CREDIT));

        assertThat(result.status()).isEqualTo(TransactionStatus.PENDING);
        verify(eventPublisher).publishRetryRequested(any());
    }

    @Test
    @DisplayName("Regla violada: no persiste ni llama al proveedor")
    void ruleViolationShortCircuits() {
        assertThatThrownBy(() -> service.execute(command("0.50", TransactionType.CREDIT)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verifyNoInteractions(repository, provider, eventPublisher);
    }

    /*
    Casos restantes (mismo patrón):

TransactionControllerTest (@WebMvcTest + @MockitoBean): 201 en EXECUTED, 202 en PENDING, 422 con violations, 400 con type: "TRANSFER", 401 sin token.
PaymentProviderAdapterTest (WireMock): 200→Approved; 402→Rejected; 500/429/408→ProviderUnavailableException; body corrupto→ProviderUnavailableException.
TransactionPersistenceAdapterTest (@DataJpaTest + Testcontainers): round-trip de mapeo, filtros combinados, orden createdAt DESC, totalElements.
     */
}