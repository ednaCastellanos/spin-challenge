package mx.spin.transactions.adapter.out.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderErrorResponse(String status, String code, String message) { }