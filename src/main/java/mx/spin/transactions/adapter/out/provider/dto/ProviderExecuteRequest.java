package mx.spin.transactions.adapter.out.provider.dto;

import java.math.BigDecimal;

public record ProviderExecuteRequest(String accountId, String type, BigDecimal amount, String currency) { }