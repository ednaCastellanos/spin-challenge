package mx.spin.transactions.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum TransactionStatus {

    PENDING,    // aceptada localmente; resultado del proveedor aún desconocido
    EXECUTED,   // proveedor APPROVED
    REJECTED,   // proveedor rechazó por negocio (no reintentable)
    FAILED;     // fallo técnico con reintentos agotados -> DLQ

    private static final Set<TransactionStatus> TERMINAL = EnumSet.of(EXECUTED, REJECTED, FAILED);

    public boolean isTerminal() { return TERMINAL.contains(this); }

    public boolean canTransitionTo(TransactionStatus target) {
        return this == PENDING && (target == PENDING || target.isTerminal());
    }
}