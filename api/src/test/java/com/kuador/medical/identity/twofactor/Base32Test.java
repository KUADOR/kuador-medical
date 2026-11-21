package com.kuador.medical.identity.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Base32 of RFC 4648 without padding, as authenticator apps expect it in {@code otpauth://} URIs. */
class Base32Test {

    @ParameterizedTest
    @CsvSource({
        "f, MY",
        "fo, MZXQ",
        "foo, MZXW6",
        "foob, MZXW6YQ",
        "fooba, MZXW6YTB",
        "foobar, MZXW6YTBOI",
        "12345678901234567890, GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    })
    void encodesAndDecodesTheRfc4648Vectors(String plain, String encoded) {
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encode(bytes)).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(bytes);
    }

    @ParameterizedTest
    @CsvSource({"MZXW6!", "mzxw6"})
    void rejectsCharactersOutsideTheAlphabet(String encoded) {
        assertThatThrownBy(() -> Base32.decode(encoded)).isInstanceOf(IllegalArgumentException.class);
    }
}
