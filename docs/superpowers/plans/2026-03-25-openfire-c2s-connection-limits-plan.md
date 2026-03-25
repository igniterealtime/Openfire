# Openfire C2S 连接可靠性保护实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Openfire 中实现 C2S 连接的可靠性保护，包括全局连接数限制、单 IP 连接数限制、速率限制和日志监控。

**Architecture:** 在 Netty Pipeline 层添加独立的 ConnectionLimiter Handler，采用滑动时间窗口算法实现速率限制，使用 AtomicInteger 和 ConcurrentHashMap 保证线程安全。

**Tech Stack:** Netty ChannelHandler, Java Concurrent utilities, JiveGlobals XML 配置读取

---

## 文件结构

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| Create | `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionStatistics.java` | 连接统计数据持有类 |
| Create | `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionLimiter.java` | 主限制处理器 |
| Create | `xmppserver/src/test/java/org/jivesoftware/openfire/nio/ConnectionStatisticsTest.java` | 单元测试 |
| Modify | `xmppserver/src/main/java/org/jivesoftware/openfire/spi/NettyServerInitializer.java` | 在 Pipeline 中添加 ConnectionLimiter |

---

## Task 1: 创建 ConnectionStatistics 统计数据类

**Files:**
- Create: `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionStatistics.java`
- Test: `xmppserver/src/test/java/org/jivesoftware/openfire/nio/ConnectionStatisticsTest.java` (后续添加)

- [ ] **Step 1: 创建 ConnectionStatistics.java**

```java
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
                connectionsByIp.remove(ip);
                return 0;
            }
            return newValue;
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
```

- [ ] **Step 2: Commit**

```bash
git add xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionStatistics.java
git commit -m "feat: add ConnectionStatistics for C2S connection tracking

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: 创建 ConnectionLimiter Handler

**Files:**
- Create: `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionLimiter.java`

- [ ] **Step 1: 创建 ConnectionLimiter.java**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionLimiter.java
git commit -m "feat: add ConnectionLimiter for C2S connection protection

- Global max connections limit
- Per-IP max connections limit
- Rate limiting with sliding time window
- Periodic statistics logging

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: 修改 NettyServerInitializer 将 ConnectionLimiter 添加到 Pipeline

**Files:**
- Modify: `xmppserver/src/main/java/org/jivesoftware/openfire/spi/NettyServerInitializer.java:76-83`

- [ ] **Step 1: 添加 import 和 Pipeline 代码**

在 `NettyServerInitializer.java` 的 import 区域添加：
```java
import org.jivesoftware.openfire.nio.ConnectionLimiter;
```

在 `initChannel` 方法中，将:
```java
        ch.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS))
            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(isClientConnection))
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
            .addLast(businessLogicHandler);
```

修改为（仅针对 C2S 连接添加 ConnectionLimiter）：
```java
        ch.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS))
            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(isClientConnection));

        // Add ConnectionLimiter only for C2S connections
        if (isClientConnection) {
            ch.pipeline().addLast("connectionLimiter", new ConnectionLimiter());
        }

        ch.pipeline()
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
            .addLast(businessLogicHandler);
```

**注意：** 上述代码中，`ch.pipeline()` 每次调用都返回同一个 Pipeline 对象，因此可以继续链式调用。但如果希望更清晰，可以在 `initChannel` 方法开头添加：
```java
ChannelPipeline pipeline = ch.pipeline();
```
然后使用 `pipeline.addLast(...)` 替代所有 `ch.pipeline().addLast(...)`。

- [ ] **Step 2: Commit**

```bash
git add xmppserver/src/main/java/org/jivesoftware/openfire/spi/NettyServerInitializer.java
git commit -m "feat: add ConnectionLimiter to C2S pipeline

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: 创建 ConnectionStatistics 单元测试

**Files:**
- Create: `xmppserver/src/test/java/org/jivesoftware/openfire/nio/ConnectionStatisticsTest.java`

- [ ] **Step 1: 创建测试文件**

```java
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConnectionStatistics}.
 */
public class ConnectionStatisticsTest {

    private ConnectionStatistics stats;

    @BeforeEach
    public void setUp() {
        stats = new ConnectionStatistics();
    }

    @Test
    public void testIncrementDecrementTotal() {
        assertEquals(0, stats.getTotal());

        stats.incrementTotal();
        assertEquals(1, stats.getTotal());

        stats.incrementTotal();
        assertEquals(2, stats.getTotal());

        stats.decrementTotal();
        assertEquals(1, stats.getTotal());

        stats.decrementTotal();
        assertEquals(0, stats.getTotal());
    }

    @Test
    public void testIncrementDecrementForIp() {
        assertEquals(0, stats.getForIp("192.168.1.1"));

        stats.incrementForIp("192.168.1.1");
        assertEquals(1, stats.getForIp("192.168.1.1"));

        stats.incrementForIp("192.168.1.1");
        assertEquals(2, stats.getForIp("192.168.1.1"));

        stats.incrementForIp("192.168.1.2");
        assertEquals(1, stats.getForIp("192.168.1.2"));

        stats.decrementForIp("192.168.1.1");
        assertEquals(1, stats.getForIp("192.168.1.1"));

        stats.decrementForIp("192.168.1.1");
        assertEquals(0, stats.getForIp("192.168.1.1"));
        assertFalse(stats.getTopIps(10).length > 0);
    }

    @Test
    public void testRateLimiting() {
        String ip = "10.0.0.1";
        long windowMs = 1000; // 1 second window

        // Record 3 connections
        stats.recordTimestamp(ip, System.currentTimeMillis());
        stats.recordTimestamp(ip, System.currentTimeMillis());
        stats.recordTimestamp(ip, System.currentTimeMillis());

        assertEquals(3, stats.cleanAndCountInWindow(ip, windowMs));

        // Old timestamps should be cleaned
        stats.recordTimestamp(ip, System.currentTimeMillis() - 2000); // 2 seconds ago
        assertEquals(1, stats.cleanAndCountInWindow(ip, windowMs));
    }

    @Test
    public void testRejectedCounter() {
        assertEquals(0, stats.getTotalRejected());

        stats.incrementRejected();
        assertEquals(1, stats.getTotalRejected());

        stats.incrementRejected();
        assertEquals(2, stats.getTotalRejected());
    }

    @Test
    public void testGetTopIps() {
        stats.incrementForIp("192.168.1.1");
        stats.incrementForIp("192.168.1.1");
        stats.incrementForIp("192.168.1.1");
        stats.incrementForIp("192.168.1.2");
        stats.incrementForIp("10.0.0.1");

        String[][] topIps = stats.getTopIps(3);

        assertEquals(3, topIps.length);
        // Should be sorted by count descending
        assertEquals("192.168.1.1", topIps[0][0]);
        assertEquals("3", topIps[0][1]);
        assertEquals("192.168.1.2", topIps[1][0]);
        assertEquals("1", topIps[1][1]);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        Runnable incrementTask = () -> {
            for (int i = 0; i < 100; i++) {
                stats.incrementTotal();
                stats.incrementForIp("192.168.1." + (i % 10));
            }
        };

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(incrementTask);
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        assertEquals(1000, stats.getTotal());
    }
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd /home/h00913487/code/Openfire && ./mvnw test -pl xmppserver -Dtest=ConnectionStatisticsTest -q
```

预期输出：所有测试通过

- [ ] **Step 3: Commit**

```bash
git add xmppserver/src/test/java/org/jivesoftware/openfire/nio/ConnectionStatisticsTest.java
git commit -m "test: add ConnectionStatisticsTest unit tests

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: 添加 openfire.xml 配置示例

**Files:**
- Modify: 需要在 Openfire 文档中添加配置说明（配置示例可通过管理后台或直接编辑 openfire.xml）

- [ ] **Step 1: 在设计文档同级目录创建配置说明文件**

```bash
cat >> docs/superpowers/specs/2026-03-25-openfire-c2s-connection-limits-design.md << 'EOF'

## 10. 配置示例

在 Openfire 的 `openfire.xml` 配置文件中添加：

```xml
<connection-limits>
    <!-- 全局最大连接数，默认 50000 -->
    <max-total>50000</max-total>

    <!-- 单 IP 最大连接数，默认 100 -->
    <max-per-ip>100</max-per-ip>

    <!-- 速率限制：时间窗口秒数，默认 1 -->
    <rate-limit-window-seconds>1</rate-limit-window-seconds>

    <!-- 速率限制：时间窗口内最大连接数，默认 10 -->
    <rate-limit-max-connections>10</rate-limit-max-connections>
</connection-limits>
```

**说明：**
- 这些配置项是可选的。如果不配置，将使用默认值。
- 配置修改后需要重启 Openfire 生效。
- 统计日志每 60 秒打印一次，使用 INFO 级别输出到 Openfire 日志。
EOF
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-03-25-openfire-c2s-connection-limits-design.md
git commit -m "docs: add configuration example to design spec

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验证命令

所有任务完成后，运行以下命令验证构建和测试：

```bash
# 编译
./mvnw compile -pl xmppserver -q

# 运行单元测试
./mvnw test -pl xmppserver -Dtest=ConnectionStatisticsTest -q

# 完整编译（检查是否有编译错误）
./mvnw compile -q
```
