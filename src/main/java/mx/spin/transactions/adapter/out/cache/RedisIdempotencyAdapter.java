package mx.spin.transactions.adapter.out.cache;

import mx.spin.transactions.application.port.out.IdempotencyPort;
import mx.spin.transactions.domain.model.TransactionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisIdempotencyAdapter implements IdempotencyPort {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyAdapter.class);

    private static final String KEY_PREFIX = "idem:tx:";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED_PREFIX = "COMPLETED:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotencyAdapter(StringRedisTemplate redis,
                                   @Value("${app.idempotency.ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Reservation reserve(String idempotencyKey) {
        String key = key(idempotencyKey);
        try {
            // SET key IN_PROGRESS NX EX ttl -> operación atómica
            Boolean acquired = redis.opsForValue().setIfAbsent(key, IN_PROGRESS, ttl);

            if (Boolean.TRUE.equals(acquired)) return new Reservation.Acquired();

            String current = redis.opsForValue().get(key);
            if (current != null && current.startsWith(COMPLETED_PREFIX)) {
                return new Reservation.AlreadyCompleted(
                        TransactionId.of(current.substring(COMPLETED_PREFIX.length())));
            }
            return new Reservation.InProgress();

        } catch (RuntimeException e) {
            // FAIL-OPEN deliberado: Redis caído no debe impedir ejecutar transacciones.
            // Se sacrifica la garantía de idempotencia para preservar disponibilidad.
            log.error("Redis unavailable; proceeding WITHOUT idempotency guarantee. key={}", idempotencyKey, e);
            return new Reservation.Acquired();
        }
    }

    @Override
    public void complete(String idempotencyKey, TransactionId transactionId) {
        try {
            redis.opsForValue().set(key(idempotencyKey), COMPLETED_PREFIX + transactionId, ttl);
        } catch (RuntimeException e) {
            log.error("Could not mark idempotency key as completed. key={}", idempotencyKey, e);
        }
    }

    @Override
    public void release(String idempotencyKey) {
        try {
            redis.delete(key(idempotencyKey));
        } catch (RuntimeException e) {
            log.error("Could not release idempotency key. key={}", idempotencyKey, e);
        }
    }

    private String key(String idempotencyKey) { return KEY_PREFIX + idempotencyKey; }
}