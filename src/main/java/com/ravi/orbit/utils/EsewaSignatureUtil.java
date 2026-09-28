package com.ravi.orbit.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class EsewaSignatureUtil {

    private EsewaSignatureUtil() {}

    public static String generateSignature(String totalAmount, String transactionUuid, String productCode, String secretKey) {

        String message = "total_amount=" + totalAmount
                        + ",transaction_uuid=" + transactionUuid
                        + ",product_code=" + productCode;

        return generateHmacSha256(message, secretKey);
    }

    public static String generateHmacSha256(String message, String secretKey) {

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(
                            secretKey.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                    );

            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate eSewa signature", e);
        }
    }
}