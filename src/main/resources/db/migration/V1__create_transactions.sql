CREATE TABLE transactions (
                              id                      UUID            PRIMARY KEY,
                              account_id              VARCHAR(64)     NOT NULL,
                              type                    VARCHAR(16)     NOT NULL,
                              amount                  NUMERIC(19, 4)  NOT NULL,
                              currency                CHAR(3)         NOT NULL,
                              description             VARCHAR(255),
                              status                  VARCHAR(16)     NOT NULL,
                              provider_transaction_id VARCHAR(64),
                              balance_after           NUMERIC(19, 4),
                              failure_code            VARCHAR(64),
                              failure_message         VARCHAR(500),
                              idempotency_key         VARCHAR(128),
                              retry_attempts          SMALLINT        NOT NULL DEFAULT 0,
                              created_at              TIMESTAMPTZ     NOT NULL,
                              updated_at              TIMESTAMPTZ     NOT NULL,

                              CONSTRAINT chk_amount_positive CHECK (amount > 0),
                              CONSTRAINT chk_type   CHECK (type   IN ('CREDIT', 'DEBIT')),
                              CONSTRAINT chk_status CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED', 'FAILED'))
);

-- Patrón de acceso dominante: historial por cuenta, más reciente primero.
CREATE INDEX idx_tx_account_created ON transactions (account_id, created_at DESC);

-- Índice parcial: sólo interesa barrer las que siguen "vivas" (reconciliación / DLQ).
CREATE INDEX idx_tx_pending ON transactions (status, created_at)
    WHERE status IN ('PENDING', 'FAILED');

-- Segunda línea de defensa contra doble ejecución en el proveedor.
CREATE UNIQUE INDEX uq_tx_provider_id ON transactions (provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE UNIQUE INDEX uq_tx_idempotency ON transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN transactions.balance_after IS 'Saldo devuelto por el proveedor. El proveedor es la fuente de verdad; aquí sólo se registra el snapshot.';