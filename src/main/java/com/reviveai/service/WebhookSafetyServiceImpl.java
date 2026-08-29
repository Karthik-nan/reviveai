package com.reviveai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Service
public class WebhookSafetyServiceImpl
        implements WebhookSafetyService {

    private static final String HMAC_SHA256 =
            "HmacSHA256";

    // =========================================================
    // SIGNATURE VALIDATION
    // =========================================================

    @Override
    public boolean isValidSignature(
            String payload,
            String signature,
            String secret
    ) {

        // -----------------------------------------------------
        // 1. VALIDATE INPUT
        // -----------------------------------------------------

        if (payload == null
                || payload.isBlank()
                || signature == null
                || signature.isBlank()
                || secret == null
                || secret.isBlank()) {

            log.warn(
                    "Webhook signature validation failed because " +
                            "payload, signature, or secret is missing"
            );

            return false;
        }

        // -----------------------------------------------------
        // 2. CALCULATE HMAC SHA256
        // -----------------------------------------------------

        try {

            Mac sha256Hmac =
                    Mac.getInstance(HMAC_SHA256);

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            secret.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            HMAC_SHA256
                    );

            sha256Hmac.init(secretKey);

            byte[] hash =
                    sha256Hmac.doFinal(
                            payload.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            String calculatedSignature =
                    HexFormat.of().formatHex(hash);

            // -------------------------------------------------
            // 3. CONSTANT-TIME COMPARISON
            // -------------------------------------------------

            boolean valid =
                    MessageDigest.isEqual(
                            calculatedSignature
                                    .toLowerCase()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    ),

                            signature
                                    .toLowerCase()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            if (!valid) {

                log.warn(
                        "Invalid Razorpay webhook signature"
                );

                return false;
            }

            // -------------------------------------------------
            // 4. SUCCESS
            // -------------------------------------------------

            log.info(
                    "Razorpay webhook signature validated successfully"
            );

            return true;

        } catch (Exception exception) {

            log.error(
                    "Unexpected error during webhook signature validation",
                    exception
            );

            return false;
        }
    }
}

