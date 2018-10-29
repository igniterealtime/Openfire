package org.jivesoftware.util;

import org.apache.commons.io.output.NullOutputStream;
import org.jivesoftware.util.cache.CacheSizes;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
    public void equalsIsAppropriate() {

        assertThat(CacheableOptional.of("my-test"), is(CacheableOptional.of("my-test")));
        assertThat(CacheableOptional.of("my-test"), is(not(CacheableOptional.of("not-my-test"))));

    }

    @Test
    public void hashCodeIsAppropriate() {

        assertThat(CacheableOptional.of("my-test").hashCode(), is(CacheableOptional.of("my-test").hashCode()));
        assertThat(CacheableOptional.of("my-test").hashCode(), is(not(CacheableOptional.of("not-my-test").hashCode())));

    }

    @Test
    public void cacheSizeOfAbsentCacheableOptionalStringIsCorrect() throws Exception {

        final CacheableOptional<String> co = CacheableOptional.of(null);

        final int actualCachedSize = calculateCachedSize(co);

        assertThat(co.getCachedSize(), is(actualCachedSize));
    }

    @Test
    public void cacheSizeOfPresentCacheableOptionalStringIsCorrect() throws Exception {

        final CacheableOptional<String> co = CacheableOptional.of("my-test");

        final int actualCachedSize = calculateCachedSize(co);

        assertThat(co.getCachedSize(), is(actualCachedSize));
    }

    @Test
    public void cacheSizeOfAbsentCacheableOptionalBooleanIsCorrect() throws Exception {

        final CacheableOptional<Boolean> co = CacheableOptional.of(null);

        final int actualCachedSize = calculateCachedSize(co);

        assertThat(co.getCachedSize(), is(actualCachedSize));
    }

    // FIXME: I would expect the serialisation overhead to be constant, but that's not the case
    @Ignore
    @Test
    public void cacheSizeOfPresentCacheableOptionalBooleanIsCorrect() throws Exception {

        final CacheableOptional<Boolean> co = CacheableOptional.of(true);

        final int actualCachedSize = calculateCachedSize(co);

        assertThat(co.getCachedSize(), is(actualCachedSize));
    }

    private int calculateCachedSize(CacheableOptional co) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(co);
        return os.size();
    }
}
