package com.reviveai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class WebhookSafetyServiceImpl implements WebhookSafetyService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:event:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isValidSignature(
            String payload,
            String signature,
            String secret
    ) {

        if (
                payload == null
                        || payload.isBlank()
                        || signature == null
                        || signature.isBlank()
                        || secret == null
                        || secret.isBlank()
        ) {
            return false;
        }

        try {

            Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8),
                            HMAC_SHA256
                    );

            sha256Hmac.init(secretKey);

            byte[] hash =
                    sha256Hmac.doFinal(
                            payload.getBytes(StandardCharsets.UTF_8)
                    );

            String calculatedSignature =
                    HexFormat.of().formatHex(hash);

            System.out.println("========== WEBHOOK DEBUG ==========");
            System.out.println("Payload length: " + payload.length());
            System.out.println("Received signature: " + signature);
            System.out.println("Calculated signature: " + calculatedSignature);
            System.out.println("Secret length: " + secret.length());
            System.out.println("===================================");

            // Constant-time comparison
            return MessageDigest.isEqual(
                    calculatedSignature
                            .toLowerCase()
                            .getBytes(StandardCharsets.UTF_8),

                    signature
                            .toLowerCase()
                            .getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {

            return false;
        }
    }

    @Override
    public boolean acquireIdempotencyLock(String eventId) {

        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        String lockKey =
                IDEMPOTENCY_PREFIX + eventId;

        Boolean acquired =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                lockKey,
                                "LOCKED",
                                IDEMPOTENCY_TTL
                        );

        return Boolean.TRUE.equals(acquired);
    }
}