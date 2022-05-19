package io.surati.gap.platform;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

final class MainTest {

    @Test
    void defaultTest() {
        MatcherAssert.assertThat(
            1L,
            Matchers.not(2L)
        );
    }
}
