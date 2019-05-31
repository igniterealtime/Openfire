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
package org.jivesoftware.openfire.archive;

import java.time.Instant;

/**
 * A to-be-archived entity.
 *
 * Note that the ordering imposed by the Comparable implementation is not consistent with equals, and serves only
 * to order instances by their creation timestamp.
 */
public class ArchiveCandidate<E> implements Comparable<ArchiveCandidate<E>> {
    private final Instant creation = Instant.now();

    private final E element;

    public ArchiveCandidate( E element ) {
        if ( element == null ) {
            throw new IllegalArgumentException( "Argument 'element' cannot be null." );
        }
        this.element = element;
    }

    public Instant createdAt()
    {
        return creation;
    }

    public E getElement()
    {
        return element;
    }

    @Override
    public int compareTo( ArchiveCandidate<E> o )
    {
        return creation.compareTo( o.creation );
    }
}
