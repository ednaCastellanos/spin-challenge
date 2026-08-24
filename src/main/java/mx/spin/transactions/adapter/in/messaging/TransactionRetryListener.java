package mx.spin.transactions.adapter.in.messaging;

import mx.spin.transactions.adapter.in.messaging.dto.TransactionRetryMessage;
import mx.spin.transactions.application.service.RetryTransactionService;
import mx.spin.transactions.domain.exception.BusinessRuleViolationException;
import mx.spin.transactions.domain.exception.ProviderRejectedException;
import mx.spin.transactions.domain.exception.TransactionNotFoundException;
import mx.spin.transactions.domain.model.TransactionId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class TransactionRetryListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionRetryListener.class);

    private final RetryTransactionService retryTransaction;

    public TransactionRetryListener(RetryTransactionService retryTransaction) {
        this.retryTransaction = retryTransaction;
    }

    /** Backoff: 5s -> 30s -> 120s -> DLT. */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5_000, multiplier = 6.0, maxDelay = 120_000),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true",
            // Errores NO reintentables: van directo a la DLQ.
            exclude = { ProviderRejectedException.class,
                    BusinessRuleViolationException.class,
                    TransactionNotFoundException.class })
    @KafkaListener(topics = "${app.kafka.retry-topic}", groupId = "transaction-retry-group")
    public void onRetryRequested(@Payload TransactionRetryMessage message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Processing retry from topic={} transactionId={}", topic, message.transactionId());
        retryTransaction.retry(TransactionId.of(message.transactionId()));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, TransactionRetryMessage> record) {
        TransactionRetryMessage message = record.value();
        if (message == null) {
            log.error("Unparseable message in DLQ. offset={}", record.offset());
            return;
        }
        log.error("Retries exhausted. transactionId={} -> FAILED", message.transactionId());
        retryTransaction.markAsFailed(TransactionId.of(message.transactionId()),
                "Retries exhausted: " + message.cause());
    }
}