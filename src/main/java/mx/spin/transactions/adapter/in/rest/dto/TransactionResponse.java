package mx.spin.transactions.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        String id, String accountId, String type, BigDecimal amount, String currency,
        String description, String status, String providerTransactionId,
        BigDecimal balanceAfter, Instant createdAt) { }