/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtSha256NoneSaslServerTest {
    @Test
    void compatibilityAliasUsesHtSha256None() {
        assertEquals(FastTokenManager.HT_SHA_256_NONE, new HtSha256NoneSaslServer().getMechanismName());
    }
}
