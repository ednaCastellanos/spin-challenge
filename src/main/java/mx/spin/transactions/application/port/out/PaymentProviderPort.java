package mx.spin.transactions.application.port.out;

import mx.spin.transactions.domain.model.ProviderResult;
import mx.spin.transactions.domain.model.Transaction;

public interface PaymentProviderPort {
    /**
     * @return Approved o Rejected (resultados de negocio)
     * @throws mx.spin.transactions.domain.exception.ProviderUnavailableException fallo técnico reintentable
     */
    ProviderResult execute(Transaction transaction);
}