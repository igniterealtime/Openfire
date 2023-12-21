/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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

public interface ServerSession extends Session {

    enum AuthenticationMethod {
        DIALBACK,
        SASL_EXTERNAL,
        OTHER
    }

    /**
     * Obtain method that was used to authenticate this session. Null when the session is not authenticated.
     *
     * @return the method used for authentication (possibly null).
     */
    AuthenticationMethod getAuthenticationMethod();

    /**
     * Returns true if this server session was authenticated using server dialback.
     *
     * @return true if this server session was authenticated using server dialback.
     */
    default boolean isUsingServerDialback() {
        return getAuthenticationMethod() == AuthenticationMethod.DIALBACK;
    }

    /**
     * Returns true if this server session was authenticated using the SASL EXTERNAL mechanism.
     *
     * @return true if this server session was authenticated using the SASL EXTERNAL mechanism.
     */
    default boolean isUsingSaslExternal() {
        return getAuthenticationMethod() == AuthenticationMethod.SASL_EXTERNAL;
    }
}
