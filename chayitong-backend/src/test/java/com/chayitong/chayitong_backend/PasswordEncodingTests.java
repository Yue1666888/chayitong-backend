package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncodingTests {
  private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

  @Test
  void samePasswordProducesDifferentHashesThatBothMatch() {
    String demoPassword = "TeaDemo-2026!";
    String firstHash = encoder.encode(demoPassword);
    String secondHash = encoder.encode(demoPassword);

    assertThat(firstHash).isNotEqualTo(demoPassword).isNotEqualTo(secondHash);
    assertThat(encoder.matches(demoPassword, firstHash)).isTrue();
    assertThat(encoder.matches(demoPassword, secondHash)).isTrue();
  }

  @Test
  void wrongPasswordDoesNotMatch() {
    String storedHash = encoder.encode("TeaDemo-2026!");

    assertThat(encoder.matches("Wrong-Demo-Password", storedHash)).isFalse();
  }
}
