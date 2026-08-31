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

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.UserEventAdapter;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TimerTask;

/** Owns periodic and account-lifecycle cleanup of persisted FAST credentials. */
public class FastTokenLifecycle extends BasicModule {

    public static final SystemProperty<Duration> CLEANUP_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.fast.token.cleanup-interval")
        .setDefaultValue(Duration.ofHours(1))
        .setChronoUnit(ChronoUnit.MINUTES)
        .setDynamic(false)
        .build();

    /**
     * The scheduled cleanup task, or null when the module is not started. A TimerTask cannot be
     * scheduled again after it has been cancelled, so a new instance is created on every start.
     */
    private TimerTask cleanupTask;

    private final UserEventAdapter userListener = new UserEventAdapter() {
        @Override
        public void userDeleting(final User user, final Map<String, Object> params) {
            FastTokenManager.invalidateTokens(user.getUsername());
        }

        @Override
        public void userModified(final User user, final Map<String, Object> params) {
            if (params != null && "passwordModified".equals(params.get("type"))) {
                FastTokenManager.invalidateTokens(user.getUsername());
            }
        }
    };

    public FastTokenLifecycle() {
        super("FAST token lifecycle");
    }

    @Override
    public void start() {
        UserEventDispatcher.addListener(userListener);
        cleanupTask = new TimerTask() {
            @Override
            public void run() {
                FastTokenManager.purgeExpiredTokens();
            }
        };
        TaskEngine.getInstance().schedule(cleanupTask, CLEANUP_INTERVAL.getValue(), CLEANUP_INTERVAL.getValue());
    }

    @Override
    public void stop() {
        UserEventDispatcher.removeListener(userListener);
        if (cleanupTask != null) {
            TaskEngine.getInstance().cancelScheduledTask(cleanupTask);
            cleanupTask = null;
        }
    }
}
