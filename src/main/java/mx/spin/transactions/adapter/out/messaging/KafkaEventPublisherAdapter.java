package mx.spin.transactions.adapter.out.messaging;

import mx.spin.transactions.adapter.in.messaging.dto.TransactionRetryMessage;
import mx.spin.transactions.application.port.out.TransactionEventPublisherPort;
import mx.spin.transactions.domain.event.TransactionRetryRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisherAdapter implements TransactionEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisherAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String retryTopic;

    public KafkaEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${app.kafka.retry-topic}") String retryTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.retryTopic = retryTopic;
    }

    @Override
    public void publishRetryRequested(TransactionRetryRequested event) {
        // Key = accountId: garantiza orden por cuenta y evita reintentos
        // concurrentes sobre la misma cuenta.
        String key = event.accountId().value();
        try {
            kafkaTemplate.send(retryTopic, key, TransactionRetryMessage.from(event));
            log.info("Retry event published. transactionId={} attempt={}", event.transactionId(), event.attempt());
        } catch (RuntimeException e) {
            // No se propaga: el cliente ya recibió 202. La transacción queda PENDING
            // y sería recuperada por reconciliación. Ver "Outbox pattern" en el README.
            log.error("Failed to publish retry event. transactionId={}", event.transactionId(), e);
        }
    }
}