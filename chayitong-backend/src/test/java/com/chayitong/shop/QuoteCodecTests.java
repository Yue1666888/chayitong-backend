package com.chayitong.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chayitong.config.ChayitongProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QuoteCodecTests {

    @Test
    void quotesSurviveRestartWithSharedConfiguredSecret() {
        QuoteCodec first = codec("01234567890123456789012345678901");
        QuoteCodec restarted = codec("01234567890123456789012345678901");
        var payload = new QuoteCodec.QuotePayload(1, 2, null,
                List.of(new QuoteCodec.QuoteItem(3, 4, 5, "product", "sku", 2, 100, 0)));

        String quoteId = first.encode(payload);

        assertThat(restarted.decode(quoteId)).isEqualTo(payload);
    }

    @Test
    void shortQuoteSecretIsRejected() {
        assertThatThrownBy(() -> codec("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private QuoteCodec codec(String secret) {
        var shop = new ChayitongProperties.Shop(true, 300, 1800, secret);
        var properties = new ChayitongProperties(null, null, null, null, shop);
        return new QuoteCodec(new ObjectMapper(), properties);
    }
}
