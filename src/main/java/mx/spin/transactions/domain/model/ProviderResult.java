package mx.spin.transactions.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de NEGOCIO del proveedor. Un fallo TÉCNICO no viaja aquí: sube como excepción. */
public sealed interface ProviderResult {

    record Approved(String providerTransactionId, BigDecimal balanceAfter, Instant executedAt)
            implements ProviderResult {
        public Approved {
            if (providerTransactionId == null || providerTransactionId.isBlank())
                throw new IllegalArgumentException("providerTransactionId is required");
        }
    }

    record Rejected(FailureReason reason) implements ProviderResult {
        public Rejected {
            if (reason == null) throw new IllegalArgumentException("reason is required");
        }
    }
}