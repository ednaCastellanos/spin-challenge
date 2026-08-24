package mx.spin.transactions.adapter.out.provider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import mx.spin.transactions.adapter.out.provider.dto.*;
import mx.spin.transactions.application.port.out.PaymentProviderPort;
import mx.spin.transactions.domain.exception.ProviderUnavailableException;
import mx.spin.transactions.domain.model.FailureReason;
import mx.spin.transactions.domain.model.ProviderResult;
import mx.spin.transactions.domain.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class PaymentProviderAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderAdapter.class);
    private static final String INSTANCE = "paymentProvider";

    private final RestClient restClient;
    private final ProviderProperties properties;

    public PaymentProviderAdapter(RestClient providerRestClient, ProviderProperties properties) {
        this.restClient = providerRestClient;
        this.properties = properties;
    }

    @Override
    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public ProviderResult execute(Transaction transaction) {
        var request = new ProviderExecuteRequest(
                transaction.accountId().value(), transaction.type().name(),
                transaction.money().amount(), transaction.money().currency().code());

        try {
            return restClient.post()
                    .uri(properties.executePath())
                    .body(request)
                    .exchange((req, response) -> handle(transaction, response));
        } catch (ResourceAccessException e) {          // timeout o fallo de red
            throw new ProviderUnavailableException("Provider not reachable: " + e.getMessage(), e);
        }
    }

    private ProviderResult handle(Transaction transaction, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws java.io.IOException {

        HttpStatusCode status = response.getStatusCode();

        if (status.is2xxSuccessful()) {
            ProviderExecuteResponse body = response.bodyTo(ProviderExecuteResponse.class);
            if (body == null || body.transactionId() == null) {
                throw new ProviderUnavailableException("Malformed provider response");
            }
            return new ProviderResult.Approved(body.transactionId(), body.balance(), body.executedAt());
        }

        // 5XX, 429 y 408 son TRANSITORIOS -> reintentables.
        if (status.is5xxServerError() || status.value() == 429 || status.value() == 408) {
            throw new ProviderUnavailableException("Provider transient failure: HTTP " + status.value());
        }

        // Resto de 4XX: decisión de NEGOCIO del proveedor. Terminal, nunca se reintenta.
        ProviderErrorResponse error = safeReadError(response);
        log.info("Provider rejected transaction {}: {}", transaction.id(), error.code());
        return new ProviderResult.Rejected(FailureReason.of(error.code(), error.message()));
    }

    private ProviderErrorResponse safeReadError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            ProviderErrorResponse error = response.bodyTo(ProviderErrorResponse.class);
            if (error != null && error.code() != null) return error;
        } catch (Exception e) {
            log.warn("Could not parse provider error body", e);
        }
        return new ProviderErrorResponse("REJECTED", "PROVIDER_REJECTED", "Transaction rejected by provider");
    }
}