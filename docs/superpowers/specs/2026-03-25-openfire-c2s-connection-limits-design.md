# Openfire C2S 连接可靠性保护设计方案

## 1. 概述

在 Openfire 中实现针对客户端到服务端（C2S）连接的可靠性保护措施，包括：
- 全局最大连接数限制
- 单 IP 最大连接数限制
- 连接速率限制（滑动时间窗口）
- 连接数实时统计和监控（通过日志）

## 2. 整体架构

### 2.1 组件位置

```
NettyServerInitializer Pipeline:
...
-> ConnectionLimiter (新增)  // 连接限制检查
-> NettyConnectionHandler
-> ...
```

`ConnectionLimiter` 作为 `ChannelInboundHandlerAdapter` 的子类，在连接建立的最早期进行检查。

### 2.2 新建文件

| 文件 | 职责 |
|------|------|
| `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionLimiter.java` | 主处理器：连接计数、IP 计数、速率限制 |
| `xmppserver/src/main/java/org/jivesoftware/openfire/nio/ConnectionStatistics.java` | 统计数据：当前连接数、各 IP 连接数、拒绝计数 |

### 2.3 修改文件

| 文件 | 修改内容 |
|------|----------|
| `xmppserver/src/main/java/org/jivesoftware/openfire/spi/NettyServerInitializer.java` | 在 Pipeline 中添加 ConnectionLimiter |

## 3. 配置项

配置位置：`openfire.xml`

```xml
<connection-limits>
    <!-- 全局最大连接数 -->
    <max-total>50000</max-total>
    <!-- 单 IP 最大连接数 -->
    <max-per-ip>100</max-per-ip>
    <!-- 速率限制：时间窗口秒数 -->
    <rate-limit-window-seconds>1</rate-limit-window-seconds>
    <!-- 速率限制：时间窗口内最大连接数 -->
    <rate-limit-max-connections>10</rate-limit-max-connections>
</connection-limits>
```

**默认值：**
- `max-total`: 50000
- `max-per-ip`: 100
- `rate-limit-window-seconds`: 1
- `rate-limit-max-connections`: 10

## 4. 限制逻辑

当连接请求到达 `ConnectionLimiter.handlerAdded()` 时：

```
1. 检查 全局连接数 是否 >= max-total
   -> 是：拒绝连接，打印日志 [ConnectionLimiter] Rejected: total connections exceeded

2. 检查 单IP连接数 是否 >= max-per-ip
   -> 是：拒绝连接，打印日志 [ConnectionLimiter] Rejected: per-IP limit exceeded

3. 检查 速率窗口内连接数 是否 >= rate-limit-max-connections
   -> 是：拒绝连接，打印日志 [ConnectionLimiter] Rejected: rate limit exceeded

4. 全部通过：
   - 全局计数 +1
   - IP 计数 +1
   - 速率窗口记录当前时间戳
   - 放行到下一个 Handler
```

当连接关闭时（`channelInactive()`）：
- 全局计数 -1
- IP 计数 -1

## 5. 数据结构

### 5.1 ConnectionStatistics

```java
public class ConnectionStatistics {
    // 全局连接计数
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    // 按 IP 索引的连接计数
    private final ConcurrentHashMap<String, AtomicInteger> connectionsByIp = new ConcurrentHashMap<>();

    // 速率窗口：记录每次连接时间戳
    private final ConcurrentLinkedQueue<Long> connectionTimestamps = new ConcurrentLinkedQueue<>();
}
```

### 5.2 速率窗口实现

滑动时间窗口算法：
1. 每次连接时，清理超过窗口时间的旧时间戳
2. 将当前时间戳加入队列
3. 如果队列长度超过限制，拒绝连接

## 6. 日志输出

定期（如每 60 秒）在后台打印统计日志：

```
[ConnectionLimiter] stats: total=1234, top_ips=[192.168.1.1:5, 10.0.0.1:3], rejected_total=10
```

**拒绝连接时立即打印：**
```
[ConnectionLimiter] Rejected connection from IP=192.168.1.1, reason=per-IP limit exceeded
```

## 7. 配置读取

在 `ConnectionLimiter` 初始化时，从 `JiveGlobals.getXMLProperty()` 读取配置值。如果配置项不存在或无效，使用默认值。

## 8. 线程安全

- 所有计数器使用 `AtomicInteger` 保证线程安全
- `connectionsByIp` 使用 `ConcurrentHashMap` 支持并发访问
- 速率窗口使用 `ConcurrentLinkedQueue` 无锁操作

## 9. 错误处理

- 如果无法获取客户端 IP（如代理场景），记录警告并放行连接
- 如果配置值非法（如负数），使用默认值并记录警告

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
