/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
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

    private final TimerTask cleanupTask = new TimerTask() {
        @Override
        public void run() {
            FastTokenManager.purgeExpiredTokens();
        }
    };

    private final UserEventAdapter userListener = new UserEventAdapter() {
        @Override
        public void userDeleting(final User user, final Map<String, Object> params) {
            FastTokenManager.invalidateTokens(user.getUsername());
        }
    };

    public FastTokenLifecycle() {
        super("FAST token lifecycle");
    }

    @Override
    public void start() {
        UserEventDispatcher.addListener(userListener);
        TaskEngine.getInstance().schedule(cleanupTask, CLEANUP_INTERVAL.getValue(), CLEANUP_INTERVAL.getValue());
    }

    @Override
    public void stop() {
        UserEventDispatcher.removeListener(userListener);
        TaskEngine.getInstance().cancelScheduledTask(cleanupTask);
    }

    void userDeleting(final User user) {
        userListener.userDeleting(user, Map.of());
    }
}
