package org.jivesoftware.util;

import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CacheableOptionalTest {

    @Test
    public void willCorrectlyRecordPresenceAndAbsence() {
        assertThat(CacheableOptional.of("my-test").isPresent(), is(true));
        assertThat(CacheableOptional.of(null).isAbsent(), is(true));
    }

    @Test
    public void willConvertToAndFromJavaOptional() {

        final Optional<String> value = Optional.of("my-test");

        final Optional<String> value2 = CacheableOptional.from(value).toOptional();

        assertThat(value, is(value2));
    }

    @Test
    public void equalsAndHashcodeAreAppropriate() {

        assertThat(CacheableOptional.of("my-test"), is(CacheableOptional.of("my-test")));
        assertThat(CacheableOptional.of("my-test").hashCode(), is(CacheableOptional.of("my-test").hashCode()));

    }

}
