package mx.spin.transactions.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import mx.spin.transactions.domain.model.TransactionType;
import java.math.BigDecimal;

public record CreateTransactionRequest(

        @NotBlank @Size(max = 64)
        @Schema(example = "acc-123456")
        String accountId,

        @NotNull
        @Schema(example = "CREDIT")
        TransactionType type,

        @NotNull @Positive
        @Digits(integer = 15, fraction = 2)
        @Schema(example = "1500.00")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        @Schema(example = "MXN")
        String currency,

        @Size(max = 255)
        @Schema(example = "Transferencia recibida")
        String description) { }