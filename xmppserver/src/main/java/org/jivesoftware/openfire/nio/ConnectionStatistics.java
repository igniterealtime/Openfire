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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds connection statistics for C2S connection limiting.
 * All methods are thread-safe.
 */
public class ConnectionStatistics {

    /** Total number of active connections */
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    /** Connection count per IP address */
    private final ConcurrentHashMap<String, AtomicInteger> connectionsByIp = new ConcurrentHashMap<>();

    /** Rate limiting: timestamps of recent connections per IP */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> connectionTimestampsByIp = new ConcurrentHashMap<>();

    /** Total number of rejected connections */
    private final AtomicInteger totalRejected = new AtomicInteger(0);

    /**
     * Increment total connection count.
     * @return new total count
     */
    public int incrementTotal() {
        return totalConnections.incrementAndGet();
    }

    /**
     * Decrement total connection count.
     * @return new total count
     */
    public int decrementTotal() {
        return totalConnections.decrementAndGet();
    }

    /**
     * Get current total connection count.
     * @return total count
     */
    public int getTotal() {
        return totalConnections.get();
    }

    /**
     * Increment connection count for an IP.
     * @param ip the IP address
     * @return new count for this IP
     */
    public int incrementForIp(String ip) {
        return connectionsByIp.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Decrement connection count for an IP.
     * @param ip the IP address
     * @return new count for this IP, or 0 if IP was removed
     */
    public int decrementForIp(String ip) {
        AtomicInteger count = connectionsByIp.get(ip);
        if (count != null) {
            int newValue = count.decrementAndGet();
            if (newValue <= 0) {
                // Only remove if the value is still the same empty counter
                connectionsByIp.remove(ip, count);
            }
            return Math.max(0, newValue);
        }
        return 0;
    }

    /**
     * Get connection count for an IP.
     * @param ip the IP address
     * @return count for this IP
     */
    public int getForIp(String ip) {
        AtomicInteger count = connectionsByIp.get(ip);
        return count != null ? count.get() : 0;
    }

    /**
     * Record a connection timestamp for rate limiting.
     * @param ip the IP address
     * @param timestamp current time in milliseconds
     */
    public void recordTimestamp(String ip, long timestamp) {
        connectionTimestampsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedQueue<>()).offer(timestamp);
    }

    /**
     * Clean old timestamps and count connections within the time window.
     * @param ip the IP address
     * @param windowMs time window in milliseconds
     * @return number of connections within the window
     */
    public int cleanAndCountInWindow(String ip, long windowMs) {
        ConcurrentLinkedQueue<Long> timestamps = connectionTimestampsByIp.get(ip);
        if (timestamps == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - windowMs;
        while (!timestamps.isEmpty() && timestamps.peek() < cutoff) {
            timestamps.poll();
        }
        return timestamps.size();
    }

    /**
     * Increment rejected connection count.
     * @return new total rejected count
     */
    public int incrementRejected() {
        return totalRejected.incrementAndGet();
    }

    /**
     * Get total rejected connection count.
     * @return total rejected count
     */
    public int getTotalRejected() {
        return totalRejected.get();
    }

    /**
     * Get a snapshot of top IPs by connection count.
     * @param limit maximum number of entries to return
     * @return array of [ip, count] pairs
     */
    public String[][] getTopIps(int limit) {
        return connectionsByIp.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
            .limit(limit)
            .map(e -> new String[]{e.getKey(), String.valueOf(e.getValue().get())})
            .toArray(String[][]::new);
    }
}
