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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.quic.QuicChannel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyConnectionTest
{
    @Test
    public void shouldResolveAddressFromDirectRemoteAddress() throws Exception
    {
        final NettyConnection connection = createConnection(
            new InetSocketAddress("203.0.113.10", 31337),
            (SocketAddress) null
        );

        assertEquals("203.0.113.10", connection.getHostAddress());
        assertArrayEquals(InetAddress.getByName("203.0.113.10").getAddress(), connection.getAddress());
    }

    @Test
    public void shouldResolveAddressFromParentChannelWhenRemoteAddressIsNotInetSocketAddress() throws Exception
    {
        final NettyConnection connection = createConnection(
            new SocketAddress() {},
            new InetSocketAddress("198.51.100.25", 44444)
        );

        assertEquals("198.51.100.25", connection.getHostAddress());
        assertArrayEquals(InetAddress.getByName("198.51.100.25").getAddress(), connection.getAddress());
    }

    @Test
    public void shouldResolveAddressFromQuicChannelRemoteSocketAddress() throws Exception
    {
        final Channel quicParent = createQuicParentChannel(
            new QuicStreamSocketAddress(),
            new InetSocketAddress("192.0.2.90", 5222)
        );
        final NettyConnection connection = createConnection(new QuicStreamSocketAddress(), quicParent);

        assertEquals("192.0.2.90", connection.getHostAddress());
        assertArrayEquals(InetAddress.getByName("192.0.2.90").getAddress(), connection.getAddress());
    }

    @Test
    public void shouldThrowUnknownHostWhenNoInetSocketAddressIsAvailable()
    {
        final NettyConnection connection = createConnection(
            new SocketAddress() {},
            new SocketAddress() {}
        );

        assertThrows(UnknownHostException.class, connection::getHostAddress);
        assertThrows(UnknownHostException.class, connection::getHostName);
        assertThrows(UnknownHostException.class, connection::getAddress);
    }

    private NettyConnection createConnection(final SocketAddress remoteAddress, final SocketAddress parentRemoteAddress)
    {
        final Channel parent = parentRemoteAddress == null ? null : createChannel(parentRemoteAddress, null);
        return createConnection(remoteAddress, parent);
    }

    private NettyConnection createConnection(final SocketAddress remoteAddress, final Channel parent)
    {
        final Channel channel = createChannel(remoteAddress, parent);
        final ChannelHandlerContext context = createContext(channel);
        return new NettyConnection(context, null, null);
    }

    private Channel createQuicParentChannel(final SocketAddress remoteAddress, final SocketAddress remoteSocketAddress)
    {
        return createProxy(new Class[] {QuicChannel.class}, (proxy, method, args) -> {
            if ("remoteAddress".equals(method.getName())) {
                return remoteAddress;
            }
            if ("remoteSocketAddress".equals(method.getName())) {
                return remoteSocketAddress;
            }
            return defaultValue(method);
        });
    }

    private Channel createChannel(final SocketAddress remoteAddress, final Channel parent)
    {
        return createProxy(Channel.class, (proxy, method, args) -> switch (method.getName()) {
            case "remoteAddress" -> remoteAddress;
            case "parent" -> parent;
            default -> defaultValue(method);
        });
    }

    private ChannelHandlerContext createContext(final Channel channel)
    {
        return createProxy(ChannelHandlerContext.class, (proxy, method, args) -> {
            if ("channel".equals(method.getName())) {
                return channel;
            }
            return defaultValue(method);
        });
    }

    private <T> T createProxy(final Class<T> type, final InvocationHandler invocationHandler)
    {
        return createProxy(new Class[] {type}, invocationHandler);
    }

    private <T> T createProxy(final Class[] types, final InvocationHandler invocationHandler)
    {
        return (T) Proxy.newProxyInstance(types[0].getClassLoader(), types, invocationHandler);
    }

    private Object defaultValue(final Method method)
    {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "proxy-" + method.getDeclaringClass().getSimpleName();
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> false;
                default -> null;
            };
        }

        final Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class QuicStreamSocketAddress extends SocketAddress
    {
        private static final long serialVersionUID = 1L;
    }
}
