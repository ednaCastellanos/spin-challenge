package mx.spin.transactions.application.service;

import mx.spin.transactions.application.port.in.ExecuteTransactionUseCase;
import mx.spin.transactions.application.port.in.command.ExecuteTransactionCommand;
import mx.spin.transactions.application.port.out.IdempotencyPort;
import mx.spin.transactions.application.port.out.TransactionRepositoryPort;
import mx.spin.transactions.domain.exception.BusinessRuleViolationException;
import mx.spin.transactions.domain.exception.DuplicateRequestException;
import mx.spin.transactions.domain.exception.ProviderRejectedException;
import mx.spin.transactions.domain.exception.TransactionNotFoundException;
import mx.spin.transactions.domain.model.*;
import mx.spin.transactions.domain.policy.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotentExecuteTransactionDecorator")
class IdempotentExecuteTransactionDecoratorTest {

    private static final String KEY = "11111111-1111-1111-1111-111111111111";
    private static final Instant NOW = Instant.parse("2025-03-15T10:30:00Z");

    @Mock private ExecuteTransactionUseCase delegate;
    @Mock private IdempotencyPort idempotency;
    @Mock private TransactionRepositoryPort repository;

    private IdempotentExecuteTransactionDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new IdempotentExecuteTransactionDecorator(delegate, idempotency, repository);
    }

    // ─────────────────────────────── Fixtures ───────────────────────────────

    private ExecuteTransactionCommand command(String idempotencyKey) {
        return new ExecuteTransactionCommand(
                "acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"),
                "MXN", "Transferencia recibida", idempotencyKey);
    }

    private Transaction pendingTransaction(TransactionId id) {
        return Transaction.pending(
                id, new AccountId("acc-123456"), TransactionType.CREDIT,
                Money.of(new BigDecimal("1500.00"), "MXN"),
                "Transferencia recibida", KEY, NOW);
    }

    private Transaction executedTransaction(TransactionId id) {
        Transaction transaction = pendingTransaction(id);
        transaction.markExecuted("txn-789", new BigDecimal("5500.00"), NOW);
        return transaction;
    }

    private Transaction rejectedTransaction(TransactionId id) {
        Transaction transaction = pendingTransaction(id);
        transaction.markRejected(FailureReason.of("INSUFFICIENT_FUNDS", "not enough balance"), NOW);
        return transaction;
    }

    private BusinessRuleViolationException ruleViolation() {
        return new BusinessRuleViolationException(
                List.of(new RuleViolation("AMOUNT_BELOW_MINIMUM", "Amount must be greater than 1.00")));
    }

    // ──────────────────────────── Sin clave ────────────────────────────

    @Nested
    @DisplayName("cuando no se envía Idempotency-Key")
    class WithoutKey {

        @Test
        @DisplayName("delega directamente sin tocar Redis")
        void delegatesDirectlyWhenKeyIsNull() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(delegate.execute(any())).thenReturn(executedTransaction(id));

            Transaction result = decorator.execute(command(null));

            assertThat(result.id()).isEqualTo(id);
            verify(delegate).execute(any());
            verifyNoInteractions(idempotency, repository);
        }

        @Test
        @DisplayName("una clave en blanco se trata como ausente")
        void treatsBlankKeyAsAbsent() {
            when(delegate.execute(any())).thenReturn(executedTransaction(new TransactionId(UUID.randomUUID())));

            decorator.execute(command("   "));

            verifyNoInteractions(idempotency);
        }
    }

    // ──────────────────────── Primera ejecución ────────────────────────

    @Nested
    @DisplayName("cuando la clave se reserva por primera vez")
    class FirstExecution {

        @Test
        @DisplayName("ejecuta y marca la clave como completada")
        void executesAndCompletesKey() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.Acquired());
            when(delegate.execute(any())).thenReturn(executedTransaction(id));

            Transaction result = decorator.execute(command(KEY));

            assertThat(result.status()).isEqualTo(TransactionStatus.EXECUTED);
            verify(idempotency).complete(KEY, id);
            verify(idempotency, never()).release(anyString());
        }

        @Test
        @DisplayName("una transacción PENDING también fija la clave")
        void completesKeyForPendingTransaction() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.Acquired());
            when(delegate.execute(any())).thenReturn(pendingTransaction(id));

            Transaction result = decorator.execute(command(KEY));

            assertThat(result.status()).isEqualTo(TransactionStatus.PENDING);
            verify(idempotency).complete(KEY, id);
        }
    }

    // ─────────────────────────── Reintentos ───────────────────────────

    @Nested
    @DisplayName("cuando la clave ya fue usada")
    class Replay {

        @Test
        @DisplayName("petición en curso: lanza DuplicateRequestException")
        void rejectsInFlightDuplicate() {
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.InProgress());

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(DuplicateRequestException.class)
                    .hasMessageContaining(KEY);

            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("ya completada: devuelve el resultado original sin re-ejecutar")
        void replaysCompletedTransaction() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.AlreadyCompleted(id));
            when(repository.findById(id)).thenReturn(Optional.of(executedTransaction(id)));

            Transaction result = decorator.execute(command(KEY));

            assertThat(result.id()).isEqualTo(id);
            assertThat(result.providerTransactionId()).isEqualTo("txn-789");
            verifyNoInteractions(delegate);   // el proveedor NO se vuelve a llamar
        }

        @Test
        @DisplayName("ya rechazada: reproduce el mismo 422 sin volver a llamar al proveedor")
        void replaysRejectionAsException() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.AlreadyCompleted(id));
            when(repository.findById(id)).thenReturn(Optional.of(rejectedTransaction(id)));

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(ProviderRejectedException.class)
                    .satisfies(e -> assertThat(((ProviderRejectedException) e).reason().code())
                            .isEqualTo("INSUFFICIENT_FUNDS"));

            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("clave completada pero transacción ausente: TransactionNotFoundException")
        void failsWhenReferencedTransactionIsMissing() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.AlreadyCompleted(id));
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    // ─────────────────────── Manejo de errores ───────────────────────

    @Nested
    @DisplayName("manejo de errores durante la ejecución")
    class ErrorHandling {

        @Test
        @DisplayName("rechazo del proveedor: FIJA la clave (resultado terminal)")
        void completesKeyOnBusinessRejection() {
            TransactionId id = new TransactionId(UUID.randomUUID());
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.Acquired());
            when(delegate.execute(any())).thenThrow(new ProviderRejectedException(
                    id, FailureReason.of("INSUFFICIENT_FUNDS", "not enough balance")));

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(ProviderRejectedException.class);

            verify(idempotency).complete(KEY, id);
            verify(idempotency, never()).release(anyString());
        }

        @Test
        @DisplayName("violación de reglas: LIBERA la clave para permitir corrección")
        void releasesKeyOnRuleViolation() {
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.Acquired());
            when(delegate.execute(any())).thenThrow(ruleViolation());

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(BusinessRuleViolationException.class);

            verify(idempotency).release(KEY);
            verify(idempotency, never()).complete(anyString(), any());
        }

        @Test
        @DisplayName("error inesperado: LIBERA la clave")
        void releasesKeyOnUnexpectedError() {
            when(idempotency.reserve(KEY)).thenReturn(new IdempotencyPort.Reservation.Acquired());
            when(delegate.execute(any())).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> decorator.execute(command(KEY)))
                    .isInstanceOf(IllegalStateException.class);

            verify(idempotency).release(KEY);
        }
    }
}