package org.xmpp.jnodes;

import org.xmpp.jnodes.nio.DatagramListener;
import org.xmpp.jnodes.nio.SelDatagramChannel;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class RelayChannel {

    private final SelDatagramChannel channelA;
    private final SelDatagramChannel channelB;
    private final SocketAddress addressA;
    private final SocketAddress addressB;
    private SocketAddress lastReceivedA;
    private SocketAddress lastReceivedB;
    private final SelDatagramChannel channelA_;
    private final SelDatagramChannel channelB_;
    private SocketAddress lastReceivedA_;
    private SocketAddress lastReceivedB_;        
    private long lastReceivedTimeA;
    private long lastReceivedTimeB;
    private final int portA;
    private final int portB;
    private final String ip;
    private Object attachment;

    public static RelayChannel createLocalRelayChannel(final String host, final int minPort, final int maxPort) throws IOException {
        int range = maxPort - minPort;
        IOException be = null;

        for (int t = 0; t < 50; t++) {
            try {
                int a = Math.round((int) (Math.random() * range)) + minPort;
                a = a % 2 == 0 ? a : a + 1;
                return new RelayChannel(host, a);
            } catch (BindException e) {
                be = e;
            } catch (IOException e) {
                be = e;
            }
        }
        throw be;
    }

    public RelayChannel(final String host, final int portA) throws IOException {

        final int portB = portA + 2;

        addressA = new InetSocketAddress(host, portA);
        addressB = new InetSocketAddress(host, portB);

        channelA = SelDatagramChannel.open(null, addressA);
        channelB = SelDatagramChannel.open(null, addressB);

        channelA.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedA = address;
                lastReceivedTimeA = System.currentTimeMillis();

                if (lastReceivedB != null) {
                    try {
                        buffer.flip();
                        channelB.send(buffer, lastReceivedB);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        channelB.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedB = address;
                lastReceivedTimeB = System.currentTimeMillis();
                if (lastReceivedA != null) {
                    try {
                        buffer.flip();
                        channelA.send(buffer, lastReceivedA);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.portA = portA;
        this.portB = portB;

        // RTCP Support
        SocketAddress addressA_ = new InetSocketAddress(host, portA + 1);
        SocketAddress addressB_ = new InetSocketAddress(host, portB + 1);

        channelA_ = SelDatagramChannel.open(null, addressA_);
        channelB_ = SelDatagramChannel.open(null, addressB_);

        channelA_.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedA_ = address;

                if (lastReceivedB_ != null) {
                    try {
                        buffer.flip();
                        channelB_.send(buffer, lastReceivedB_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        channelB_.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedB_ = address;
                if (lastReceivedA_ != null) {
                    try {
                        buffer.flip();
                        channelA_.send(buffer, lastReceivedA_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.ip = host;
    }

    public SocketAddress getAddressB() {
        return addressB;
    }

    public SocketAddress getAddressA() {
        return addressA;
    }

    public int getPortA() {
        return portA;
    }

    public int getPortB() {
        return portB;
    }

    public String getIp() {
        return ip;
    }

    public long getLastReceivedTimeA() {
        return lastReceivedTimeA;
    }

    public long getLastReceivedTimeB() {
        return lastReceivedTimeB;
    }

    public Object getAttachment() {
        return attachment;
    }

    public void setAttachment(Object attachment) {
        this.attachment = attachment;
    }

    public void close() {
        try {
            channelA.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelB.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelA_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelB_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SelDatagramChannel getChannelA() {
        return channelA;
    }

    public SelDatagramChannel getChannelB() {
        return channelB;
    }

    public SelDatagramChannel getChannelA_() {
        return channelA_;
    }

    public SelDatagramChannel getChannelB_() {
        return channelB_;
    }
}
