package org.jivesoftware.util;

import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class SentryWrap {
    static class OrphanSpan extends IOException {

    }

    @FunctionalInterface
    public interface VoidCallable {
        void call() throws Exception;
    }

    public static <T> T span(Callable<T> s, String name, String op) throws Exception {
        ISpan parent = Sentry.getSpan();
        if (parent == null) {
            try {
                throw new OrphanSpan();
            } catch(OrphanSpan e) {
                Sentry.captureException(e);
            }
            return transaction(s, name, op);
        }
        ISpan span = parent.startChild(name, op);
        try {
            return s.call();
        } catch(Exception e) {
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            span.finish();
        }
    }
    public static void span(VoidCallable r, String name, String op) throws Exception {
        span(() -> { r.call(); return null; }, name, op);
    }

    public static <T> T transaction(Callable<T> s, String name, String op) throws Exception {
        Sentry.pushScope();
        Sentry.clearBreadcrumbs();
        ITransaction trans = Sentry.startTransaction(name, op, true);
        try {
            return s.call();
        } catch(Exception e) {
            trans.setStatus(SpanStatus.INTERNAL_ERROR);
            Sentry.captureException(e);
            throw e;
        } finally {
            trans.finish();
            Sentry.popScope();
        }
    }

    public static void transaction(VoidCallable r, String s1, String s2) throws Exception {
        transaction(() -> {
            r.call();
            return null;
        }, s1, s2);
    }
}
