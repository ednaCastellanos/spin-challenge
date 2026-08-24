package mx.spin.transactions.domain.exception;

/** Fallo TÉCNICO del proveedor (5XX, timeout, circuito abierto). Reintentable. */
/*
ProviderUnavailableException no tiene handler a propósito: nunca debe escapar del servicio. Si escapara, cae en handleUnexpected y el log lo delata como bug. Es una decisión deliberada, no un olvido.
 */
public class ProviderUnavailableException extends DomainException {

    public ProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
    public ProviderUnavailableException(String message) { super(message); }
    @Override public String code() { return "PROVIDER_UNAVAILABLE"; }
}