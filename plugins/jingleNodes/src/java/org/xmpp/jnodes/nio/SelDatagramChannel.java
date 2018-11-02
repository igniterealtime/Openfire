package org.xmpp.jnodes.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelDatagramChannel {

    private final static ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static Selector selector;

    // Instance Properties
    protected final DatagramChannel channel;
    private DatagramListener datagramListener;
    private final static Object obj = new Object();

    private static void init() {
        try {
            selector = Selector.open();

            while (!selector.isOpen()) {
                Thread.yield();
            }

            final Runnable task = new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            final int n;

                            synchronized (obj) {
                            }
                            n = selector.select();

                            if (n == 0) {
                                Thread.sleep(50);
                                Thread.yield();
                                continue;
                            }

                            final Set keys = selector.selectedKeys();

                            // Iterate through the Set of keys.
                            for (Iterator i = keys.iterator(); i.hasNext();) {
                                // Get a key from the set, and remove it from the set
                                final SelectionKey key = (SelectionKey) i.next();
                                i.remove();

                                // Get the channel associated with the key
                                final DatagramChannel c = (DatagramChannel) key.channel();

                                if (key.isValid() && key.isReadable()) {
                                    final SelDatagramChannel sdc = (SelDatagramChannel) key.attachment();

                                    if (sdc == null) {
                                        // Discard Packet
                                        c.receive(ByteBuffer.allocate(0));
                                        continue;
                                    }

                                    final ByteBuffer b = ByteBuffer.allocateDirect(1450);
                                    final SocketAddress clientAddress;
                                    synchronized (sdc) {
                                        clientAddress = sdc.channel.receive(b);
                                    }
                                    // If we got the datagram successfully, broadcast the Event
                                    if (clientAddress != null) {
                                        // Execute in a different Thread avoid serialization
                                        if (sdc.datagramListener != null) {
                                            executorService.submit(new Runnable() {
                                                public void run() {
                                                    sdc.datagramListener.datagramReceived(sdc, b, clientAddress);
                                                }
                                            });
                                        }
                                    }

                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            };

            executorService.submit(task);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected SelDatagramChannel(final DatagramChannel channel, final DatagramListener datagramListener) {
        this.channel = channel;
        this.datagramListener = datagramListener;
    }

    public static SelDatagramChannel open(final DatagramListener datagramListener, final SocketAddress localAddress) throws IOException {
        synchronized (executorService) {
            if (selector == null) {
                init();
            }
        }

        final DatagramChannel dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.socket().bind(localAddress);
        final SelDatagramChannel c = new SelDatagramChannel(dc, datagramListener);
        synchronized (obj) {
            selector.wakeup();
            dc.register(selector, SelectionKey.OP_READ, c);
        }
        return c;
    }

    public int send(final ByteBuffer src, final SocketAddress target) throws IOException {
        return this.channel.send(src, target);
    }

    public void close() throws IOException {
        final SelectionKey k = channel.keyFor(selector);
        if (k != null) {
            synchronized (obj) {
                selector.wakeup();
                k.cancel();
            }
        }
        synchronized (this) {
            channel.close();
        }
    }

    public void setDatagramListener(DatagramListener listener) {
        this.datagramListener = listener;
    }
}
