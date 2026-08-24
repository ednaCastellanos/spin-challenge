package mx.spin.transactions.adapter.out.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderExecuteResponse(String transactionId, String status,
                                      BigDecimal balance, Instant executedAt) { }