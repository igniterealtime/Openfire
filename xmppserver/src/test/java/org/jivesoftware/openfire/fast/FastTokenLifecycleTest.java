/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
package org.jivesoftware.openfire.fast;

import org.jivesoftware.openfire.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

class FastTokenLifecycleTest {
    @Test
    void deletingUserInvalidatesAllFastTokens() {
        final User user = mock(User.class);
        when(user.getUsername()).thenReturn("alice");
        try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class)) {
            new FastTokenLifecycle().userDeleting(user);
            manager.verify(() -> FastTokenManager.invalidateTokens("alice"));
        }
    }
}
