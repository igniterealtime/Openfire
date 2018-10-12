/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A utility class that provides re-usable functionality that relates to Java collections.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CollectionUtils
{
    /**
     * Returns a stateful stream filter that, once applied to a stream, returns a stream consisting
     * of the distinct elements (according to the specified key).
     * <p>
     * The implementation of {@link Stream#distinct()} can be used to return a stream that has distinct
     * elements, based on the implementation of {@link Object#equals(Object)}. That implementation does
     * not allow to filter a stream based on one property of each object. The implementation of provided
     * by this method does.
     *
     * @param keyExtractor A function to extract the desired key from the stream objects (cannot be null).
     * @param <T>          Stream element type.
     * @return A filter
     * @see <a href="https://stackoverflow.com/questions/27870136/java-lambda-stream-distinct-on-arbitrary-key">Stack Overflow: Java Lambda Stream Distinct() on arbitrary key?</a>
     */
    public static <T> Predicate<T> distinctByKey( Function<? super T, Object> keyExtractor )
    {
        final Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent( keyExtractor.apply( t ), Boolean.TRUE ) == null;
    }
}
