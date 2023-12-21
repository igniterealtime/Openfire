/*
 * Copyright (C) 2017-2018 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

/**
 * Holds a (possibly authenticated) domain pair.
 */
public class DomainPair implements java.io.Serializable {
    private final String local;
    private final String remote;
    private static final long serialVersionUID = 1L;

    public DomainPair(String local, String remote) {
        this.local = local;
        this.remote = remote;
    }

    public String toString() {
        return "{" + local + " -> " + remote + "}";
    }

    public String getLocal() {
        return local;
    }

    public String getRemote() {
        return remote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DomainPair that = (DomainPair) o;

        if (!local.equals(that.local)) return false;
        return remote.equals(that.remote);
    }

    @Override
    public int hashCode() {
        int result = local.hashCode();
        result = 31 * result + remote.hashCode();
        return result;
    }
}
