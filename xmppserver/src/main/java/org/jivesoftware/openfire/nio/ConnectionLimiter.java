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
package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Netty Handler that enforces C2S connection limits:
 * - Global maximum connections
 * - Per-IP maximum connections
 * - Per-IP rate limiting (sliding time window)
 *
 * This handler is added to the pipeline before NettyConnectionHandler
 * to reject invalid connections as early as possible.
 */
public class ConnectionLimiter extends ChannelInboundHandlerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(ConnectionLimiter.class);

    /** Singleton instance */
    private static final ConnectionStatistics STATS = new ConnectionStatistics();

    /** Scheduled executor for periodic logging */
    private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "ConnectionLimiter-StatsLogger");
        t.setDaemon(true);
        return t;
    });

    // Configuration property keys
    private static final String PROP_MAX_TOTAL = "connection-limits.max-total";
    private static final String PROP_MAX_PER_IP = "connection-limits.max-per-ip";
    private static final String PROP_RATE_WINDOW_SECONDS = "connection-limits.rate-limit-window-seconds";
    private static final String PROP_RATE_MAX_CONNECTIONS = "connection-limits.rate-limit-max-connections";

    // Default values
    private static final int DEFAULT_MAX_TOTAL = 50000;
    private static final int DEFAULT_MAX_PER_IP = 100;
    private static final int DEFAULT_RATE_WINDOW_SECONDS = 1;
    private static final int DEFAULT_RATE_MAX_CONNECTIONS = 10;

    // Configuration values (read on first access)
    private static volatile int maxTotal = -1;
    private static volatile int maxPerIp = -1;
    private static volatile int rateWindowSeconds = -1;
    private static volatile int rateMaxConnections = -1;

    static {
        // Schedule periodic stats logging every 60 seconds
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                logStats();
            } catch (Exception e) {
                Log.warn("Error logging connection statistics", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Get the singleton statistics instance.
     * @return the connection statistics
     */
    public static ConnectionStatistics getStatistics() {
        return STATS;
    }

    private static int getMaxTotal() {
        if (maxTotal < 0) {
            maxTotal = JiveGlobals.getXMLProperty(PROP_MAX_TOTAL, DEFAULT_MAX_TOTAL);
            if (maxTotal <= 0) {
                Log.warn("Invalid {} value: {}, using default {}", PROP_MAX_TOTAL, maxTotal, DEFAULT_MAX_TOTAL);
                maxTotal = DEFAULT_MAX_TOTAL;
            }
        }
        return maxTotal;
    }

    private static int getMaxPerIp() {
        if (maxPerIp < 0) {
            maxPerIp = JiveGlobals.getXMLProperty(PROP_MAX_PER_IP, DEFAULT_MAX_PER_IP);
            if (maxPerIp <= 0) {
                Log.warn("Invalid {} value: {}, using default {}", PROP_MAX_PER_IP, maxPerIp, DEFAULT_MAX_PER_IP);
                maxPerIp = DEFAULT_MAX_PER_IP;
            }
        }
        return maxPerIp;
    }

    private static int getRateWindowSeconds() {
        if (rateWindowSeconds < 0) {
            rateWindowSeconds = JiveGlobals.getXMLProperty(PROP_RATE_WINDOW_SECONDS, DEFAULT_RATE_WINDOW_SECONDS);
            if (rateWindowSeconds <= 0) {
                Log.warn("Invalid {} value: {}, using default {}", PROP_RATE_WINDOW_SECONDS, rateWindowSeconds, DEFAULT_RATE_WINDOW_SECONDS);
                rateWindowSeconds = DEFAULT_RATE_WINDOW_SECONDS;
            }
        }
        return rateWindowSeconds;
    }

    private static int getRateMaxConnections() {
        if (rateMaxConnections < 0) {
            rateMaxConnections = JiveGlobals.getXMLProperty(PROP_RATE_MAX_CONNECTIONS, DEFAULT_RATE_MAX_CONNECTIONS);
            if (rateMaxConnections <= 0) {
                Log.warn("Invalid {} value: {}, using default {}", PROP_RATE_MAX_CONNECTIONS, rateMaxConnections, DEFAULT_RATE_MAX_CONNECTIONS);
                rateMaxConnections = DEFAULT_RATE_MAX_CONNECTIONS;
            }
        }
        return rateMaxConnections;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        String ip = getClientIp(ctx);
        if (ip == null) {
            Log.warn("Unable to determine client IP for connection from {}, allowing", ctx.channel().remoteAddress());
            super.handlerAdded(ctx);
            return;
        }

        // Check 1: Global connection limit
        if (STATS.getTotal() >= getMaxTotal()) {
            STATS.incrementRejected();
            Log.info("[ConnectionLimiter] Rejected connection from IP={}, reason=global limit exceeded (total={})", ip, STATS.getTotal());
            reject(ctx);
            return;
        }

        // Check 2: Per-IP connection limit
        if (STATS.getForIp(ip) >= getMaxPerIp()) {
            STATS.incrementRejected();
            Log.info("[ConnectionLimiter] Rejected connection from IP={}, reason=per-IP limit exceeded (count={})", ip, STATS.getForIp(ip));
            reject(ctx);
            return;
        }

        // Check 3: Rate limiting
        long windowMs = getRateWindowSeconds() * 1000L;
        if (STATS.cleanAndCountInWindow(ip, windowMs) >= getRateMaxConnections()) {
            STATS.incrementRejected();
            Log.info("[ConnectionLimiter] Rejected connection from IP={}, reason=rate limit exceeded (window={}s, max={})", ip, getRateWindowSeconds(), getRateMaxConnections());
            reject(ctx);
            return;
        }

        // All checks passed - increment counters
        STATS.incrementTotal();
        STATS.incrementForIp(ip);
        STATS.recordTimestamp(ip, System.currentTimeMillis());

        Log.debug("[ConnectionLimiter] Accepted connection from IP={}, total={}, ip_count={}", ip, STATS.getTotal(), STATS.getForIp(ip));

        // Pass to next handler
        super.handlerAdded(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String ip = getClientIp(ctx);
        if (ip != null) {
            STATS.decrementTotal();
            STATS.decrementForIp(ip);
            Log.debug("[ConnectionLimiter] Connection closed from IP={}, total={}", ip, STATS.getTotal());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Log.debug("[ConnectionLimiter] Exception on channel from {}", ctx.channel().remoteAddress(), cause);
        super.exceptionCaught(ctx, cause);
    }

    /**
     * Get the client IP address from the channel context.
     * @param ctx the channel handler context
     * @return the IP address string, or null if unavailable
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
                InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
                return addr.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            Log.warn("Failed to get client IP from {}", ctx.channel().remoteAddress(), e);
        }
        return null;
    }

    /**
     * Reject a connection by closing the channel.
     * @param ctx the channel handler context
     */
    private void reject(ChannelHandlerContext ctx) {
        ctx.channel().close();
    }

    /**
     * Log periodic statistics.
     */
    private static void logStats() {
        if (!Log.isInfoEnabled()) {
            return;
        }
        String[][] topIps = STATS.getTopIps(5);
        StringBuilder ipStats = new StringBuilder();
        for (String[] entry : topIps) {
            if (ipStats.length() > 0) {
                ipStats.append(", ");
            }
            ipStats.append(entry[0]).append(":").append(entry[1]);
        }
        Log.info("[ConnectionLimiter] stats: total={}, top_ips=[{}], rejected_total={}", STATS.getTotal(), ipStats.toString(), STATS.getTotalRejected());
    }
}
