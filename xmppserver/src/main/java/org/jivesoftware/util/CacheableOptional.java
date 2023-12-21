/*
 * Copyright (C) 2018-2020 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.util;

import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Some times it is desirable to store in a {@link org.jivesoftware.util.cache.Cache} the absence of a value.
 * Unfortunately, it's not possible to store a {@code null} value in a clustered cache because of the underlying hazelcast
 * technology used for clustering. The obvious candidate would therefore be to store an {@link Optional} object in the
 * cache instead - unfortunately an Optional is not serializable. This class therefore performs this functionality - an
 * optional value that is cacheable.
 */
public class CacheableOptional<T extends Serializable> implements Cacheable {

    private final T value;

    private CacheableOptional(T value) {
        this.value = value;
    }

    public static <T extends Serializable> CacheableOptional<T> of(final T value) {
        return new CacheableOptional<>(value);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T extends Serializable> CacheableOptional<T> from(final Optional<T> value) {
        return new CacheableOptional<>(value.orElse(null));
    }

    public T get() {
        return value;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean isAbsent() {
        return value == null;
    }

    public Optional<T> toOptional() {
        return Optional.ofNullable(this.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CacheableOptional<?> that = (CacheableOptional<?>) o;
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "CacheableOptional{" +
            (value == null ? "absent value" : "value=" + value) +
            '}';
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        final int sizeOfValue = CacheSizes.sizeOfAnything(value);
        if (value == null) {
            // 94 bytes seems to be the overhead of a CacheableOptional representing absent value
            return 94 + sizeOfValue;
        } else {
            // 72 bytes seems to be the overhead of a CacheableOptional<String> representing present value
            return 72 + CacheSizes.sizeOfString(value.getClass().getName()) + sizeOfValue;
        }
    }
}
