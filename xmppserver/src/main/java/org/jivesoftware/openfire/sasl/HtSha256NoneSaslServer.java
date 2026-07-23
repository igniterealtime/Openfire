/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastTokenManager;

import java.util.Collections;

/**
 * Deprecated compatibility alias for {@link HtSaslServer} with mechanism
 * {@link FastTokenManager#HT_SHA_256_NONE}.
 *
 * @deprecated Use {@link HtSaslServer} instead.
 */
@Deprecated
public class HtSha256NoneSaslServer extends HtSaslServer {

    /** The SASL mechanism name. */
    public static final String MECHANISM_NAME = FastTokenManager.HT_SHA_256_NONE;

    public HtSha256NoneSaslServer() {
        super(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
    }
}
