package com.reviveai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX =
            "reviveai:idempotency:payment:";

    /*
     * How long an event remains marked as processed.
     *
     * 24 hours is appropriate for our current payment-event flow.
     */
    private static final Duration IDEMPOTENCY_TTL =
            Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * Attempts to mark a payment event as processed.
     *
     * Redis SETNX semantics are used through setIfAbsent().
     *
     * @param eventId unique payment event identifier
     * @return true  -> event was not processed before
     *         false -> event was already processed
     */
    public boolean tryMarkAsProcessed(String eventId) {

        if (eventId == null || eventId.isBlank()) {

            log.warn(
                    "Cannot perform idempotency check because eventId is null or blank"
            );

            return false;
        }

        String key =
                KEY_PREFIX + eventId;

        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(
                        key,
                        "processed",
                        IDEMPOTENCY_TTL
                );

        boolean firstProcessing =
                Boolean.TRUE.equals(created);

        if (firstProcessing) {

            log.info(
                    "Payment event marked as processed in Redis. eventId={}",
                    eventId
            );

        } else {

            log.warn(
                    "Duplicate payment event detected. eventId={}",
                    eventId
            );
        }

        return firstProcessing;
    }

    /**
     * Removes an idempotency key.
     *
     * This is useful when processing fails and we want the
     * event to be allowed to retry.
     */
    public void removeProcessedMark(String eventId) {

        if (eventId == null || eventId.isBlank()) {
            return;
        }

        String key =
                KEY_PREFIX + eventId;

        redisTemplate.delete(key);

        log.info(
                "Removed payment event idempotency key. eventId={}",
                eventId
        );
    }

    /**
     * Checks whether an event has already been processed.
     */
    public boolean isProcessed(String eventId) {

        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        String key =
                KEY_PREFIX + eventId;

        Boolean exists =
                redisTemplate.hasKey(key);

        return Boolean.TRUE.equals(exists);
    }
}