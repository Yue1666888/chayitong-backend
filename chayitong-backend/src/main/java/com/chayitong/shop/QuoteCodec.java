package com.chayitong.shop;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
final class QuoteCodec {

    private static final String ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final SecretKeySpec key;

    QuoteCodec(ObjectMapper objectMapper, com.chayitong.config.ChayitongProperties properties) {
        this.objectMapper = objectMapper;
        byte[] secret = properties.shop().quoteSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("APP_SHOP_QUOTE_SECRET must contain at least 32 bytes");
        }
        this.key = new SecretKeySpec(secret, ALGORITHM);
    }

    String encode(QuotePayload payload) {
        try {
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            return body + "." + sign(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue checkout quote", exception);
        }
    }

    QuotePayload decode(String quoteId) {
        if (quoteId == null || quoteId.isBlank()) {
            throw invalidQuote();
        }
        String[] parts = quoteId.split("\\.");
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw invalidQuote();
        }
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), QuotePayload.class);
        } catch (Exception exception) {
            throw invalidQuote();
        }
    }

    String hash(QuotePayload payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash checkout quote", exception);
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign checkout quote", exception);
        }
    }

    private static BusinessException invalidQuote() {
        return new BusinessException(ErrorCode.QUOTE_CHANGED, "报价无效，请重新结算");
    }

    record QuotePayload(long userId, long expiresAtEpochMilli, String storeId, List<QuoteItem> items) {
    }

    record QuoteItem(
            long cartItemId,
            long productId,
            long skuId,
            String productKey,
            String skuKey,
            int quantity,
            long priceFen,
            long skuVersion) {
    }
}
