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
package org.jivesoftware.openfire.fast;

import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.TaskEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.Map;
import java.util.TimerTask;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link FastTokenLifecycle} registers and deregisters its collaborators correctly,
 * and that the behaviour those registrations trigger is the intended one.
 *
 * <p>Both the user-deletion listener and the periodic cleanup task are private to the module, so
 * each test drives them the way the server does: by starting the module and then firing the event
 * or running the captured task.
 */
class FastTokenLifecycleTest {

    private FastTokenLifecycle module;
    private TaskEngine taskEngine;
    private MockedStatic<TaskEngine> taskEngineStatic;

    @BeforeEach
    void setUp() {
        taskEngine = mock(TaskEngine.class);
        taskEngineStatic = mockStatic(TaskEngine.class);
        taskEngineStatic.when(TaskEngine::getInstance).thenReturn(taskEngine);
        module = new FastTokenLifecycle();
    }

    @AfterEach
    void tearDown() {
        // UserEventDispatcher holds its listeners statically; leaving one registered
        // would leak into every subsequent test in this JVM.
        module.stop();
        taskEngineStatic.close();
    }

    /**
     * A started module must react to a user being deleted by invalidating that user's FAST tokens.
     * This covers both halves of the wiring: that the listener is registered with
     * {@link UserEventDispatcher}, and that it maps the event to the correct username.
     */
    @Test
    void deletingUserInvalidatesFastTokens() {
        final User user = mock(User.class);
        when(user.getUsername()).thenReturn("alice");

        try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class)) {
            module.start();
            UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_deleting, Map.of());

            manager.verify(
                () -> FastTokenManager.invalidateTokens("alice"),
                times(1).description("Deleting a user must invalidate that user's FAST tokens. Either " +
                    "start() failed to register the listener with UserEventDispatcher, or the listener " +
                    "did not pass the deleted user's username to FastTokenManager."));
        }
    }

    /**
     * A stopped module must be inert. If {@code stop()} fails to deregister the listener it stays in
     * the dispatcher's static listener list for the lifetime of the JVM, still touching token storage.
     */
    @Test
    void stoppedModuleNoLongerReactsToUserDeletion() {
        final User user = mock(User.class);
        when(user.getUsername()).thenReturn("alice");

        try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class)) {
            module.start();
            module.stop();
            UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_deleting, Map.of());

            manager.verify(
                () -> FastTokenManager.invalidateTokens(anyString()),
                never().description("A stopped module must ignore user deletions, but its listener was " +
                    "still invoked. stop() did not remove the listener from UserEventDispatcher, which " +
                    "leaks it for the lifetime of the JVM."));
        }
    }

    /**
     * Starting the module must schedule the recurring cleanup task at the configured interval, and
     * that task must purge expired tokens when it runs.
     */
    @Test
    void startSchedulesCleanupTaskThatPurgesExpiredTokens() {
        final ArgumentCaptor<TimerTask> task = ArgumentCaptor.forClass(TimerTask.class);
        final Duration interval = FastTokenLifecycle.CLEANUP_INTERVAL.getValue();

        module.start();

        verify(taskEngine, times(1).description("start() must schedule the cleanup task with both an " +
            "initial delay and a period equal to the configured cleanup interval; without it, expired " +
            "tokens are never purged."))
            .schedule(task.capture(), eq(interval), eq(interval));

        try (MockedStatic<FastTokenManager> manager = mockStatic(FastTokenManager.class)) {
            task.getValue().run();

            manager.verify(
                FastTokenManager::purgeExpiredTokens,
                times(1).description("The scheduled task must purge expired tokens when it runs. It was " +
                    "scheduled, but running it did not reach FastTokenManager."));
        }
    }

    /**
     * Stopping the module must cancel the very task that starting it scheduled. Cancelling a different
     * instance would silently leave the original running.
     */
    @Test
    void stopCancelsTheScheduledCleanupTask() {
        final ArgumentCaptor<TimerTask> scheduled = ArgumentCaptor.forClass(TimerTask.class);
        final ArgumentCaptor<TimerTask> cancelled = ArgumentCaptor.forClass(TimerTask.class);

        module.start();
        module.stop();

        verify(taskEngine, times(1).description("start() must schedule exactly one cleanup task; without " +
            "it there is nothing for stop() to cancel."))
            .schedule(scheduled.capture(), any(Duration.class), any(Duration.class));
        verify(taskEngine, times(1).description("stop() must cancel the cleanup task, otherwise it keeps " +
            "purging tokens after the module has shut down."))
            .cancelScheduledTask(cancelled.capture());

        assertSame(scheduled.getValue(), cancelled.getValue(),
            "stop() cancelled a different TimerTask instance than start() scheduled, so the original task " +
                "is still registered with the TaskEngine and continues to run.");
    }
}
