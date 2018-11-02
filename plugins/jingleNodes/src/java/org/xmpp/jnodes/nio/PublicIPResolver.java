package org.xmpp.jnodes.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class PublicIPResolver {

    final static byte BINDING_REQUEST_ID = 0x0001;
    final static int MAPPED_ADDRESS = 0x0001;
    final static byte CHANGE_REQUEST_NO_CHANGE[] = {0, 3, 0, 4, 0, 0, 0, 0};
    final static Random r = new Random(System.nanoTime());

    private static byte[] getHeader(final int contentLenght) {
        final byte header[] = new byte[20];
        header[0] = 0;
        header[1] = BINDING_REQUEST_ID;
        header[2] = 0;
        header[3] = (byte) contentLenght;
        header[4] = (byte) (r.nextInt(9));
        header[5] = (byte) (r.nextInt(8));
        header[6] = (byte) (r.nextInt(7));
        header[7] = (byte) (r.nextInt(6));
        return header;
    }

    public static ByteBuffer createSTUNChangeRequest() {
        final byte header[] = getHeader(CHANGE_REQUEST_NO_CHANGE.length);
        final byte data[] = new byte[header.length + CHANGE_REQUEST_NO_CHANGE.length];
        System.arraycopy(header, 0, data, 0, header.length);
        System.arraycopy(CHANGE_REQUEST_NO_CHANGE, 0, data, header.length, CHANGE_REQUEST_NO_CHANGE.length);
        return ByteBuffer.wrap(data);
    }

    public static Header parseResponse(byte[] data) {
        byte[] lengthArray = new byte[2];
        System.arraycopy(data, 2, lengthArray, 0, 2);
        int length = unsignedShortToInt(lengthArray);
        byte[] cuttedData;
        int offset = 20;

        while (length > 0) {
            cuttedData = new byte[length];
            System.arraycopy(data, offset, cuttedData, 0, length);
            Header h = parseHeader(cuttedData);

            if (h.getType() == MAPPED_ADDRESS) {
                return h;
            }
            length -= h.getLength();
            offset += h.getLength();
        }
        return null;
    }

    private static Header parseHeader(byte[] data) {
        byte[] typeArray = new byte[2];
        System.arraycopy(data, 0, typeArray, 0, 2);
        int type = unsignedShortToInt(typeArray);
        byte[] lengthArray = new byte[2];
        System.arraycopy(data, 2, lengthArray, 0, 2);
        int lengthValue = unsignedShortToInt(lengthArray);
        byte[] valueArray = new byte[lengthValue];
        System.arraycopy(data, 4, valueArray, 0, lengthValue);
        if (data.length >= 8) {
            int family = unsignedByteToInt(valueArray[1]);
            if (family == 1) {
                byte[] portArray = new byte[2];
                System.arraycopy(valueArray, 2, portArray, 0, 2);
                int port = unsignedShortToInt(portArray);
                int firstOctet = unsignedByteToInt(valueArray[4]);
                int secondOctet = unsignedByteToInt(valueArray[5]);
                int thirdOctet = unsignedByteToInt(valueArray[6]);
                int fourthOctet = unsignedByteToInt(valueArray[7]);
                final StringBuilder ip = new StringBuilder().append(firstOctet).append(".").append(secondOctet).append(".").append(thirdOctet).append(".").append(fourthOctet);
                return new Header(new InetSocketAddress(ip.toString(), port), type, lengthValue + 4);
            }
        }
        return new Header(null, -1, lengthValue + 4);
    }

    public static int unsignedShortToInt(final byte[] b) {
        int a = b[0] & 0xFF;
        int aa = b[1] & 0xFF;
        return ((a << 8) + aa);
    }

    public static int unsignedByteToInt(byte b) {
        return (int) b & 0xFF;
    }

    public static class Header {
        final InetSocketAddress address;
        final int type;
        final int length;

        public Header(final InetSocketAddress address, int type, int length) {
            this.address = address;
            this.type = type;
            this.length = length;
        }

        public int getType() {
            return type;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public int getLength() {
            return length;
        }
    }

    public static InetSocketAddress getPublicAddress(final String stunServer, final int port) {
        int lport = 10002;

        for (int t = 0; t < 3; t++) {
            try {
                final SelDatagramChannel channel = SelDatagramChannel.open(null, new InetSocketAddress(System.getProperty("os.name")!=null&&System.getProperty("os.name").toLowerCase().indexOf("win") > -1 ? LocalIPResolver.getLocalIP() : "0.0.0.0", lport));

                return getPublicAddress(channel, stunServer, port);

            } catch (IOException e) {
                lport += r.nextInt(10) + 1;
            }
        }
        return null;
    }

    public static InetSocketAddress getPublicAddress(final SelDatagramChannel channel, final String stunServer, final int port) {
        final Header[] h = new Header[1];

        try {

            channel.setDatagramListener(new DatagramListener() {
                public void datagramReceived(SelDatagramChannel channel, ByteBuffer buffer, SocketAddress address) {
                    final byte b[] = new byte[buffer.position()];
                    buffer.rewind();
                    buffer.get(b, 0, b.length);
                    h[0] = parseResponse(b);
                }
            });

            channel.send(createSTUNChangeRequest(), new InetSocketAddress(stunServer, port));
            Thread.sleep(100);
            for (int i = 0; i < 5; i++) {
                Thread.sleep(100);
                if (h[0] != null) {
                    return h[0].getAddress();
                }
                if (i % 2 == 0) {
                    channel.send(createSTUNChangeRequest(), new InetSocketAddress(stunServer, port));
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            return null;
        }
    }
}
