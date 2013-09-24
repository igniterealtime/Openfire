package org.xmpp.jnodes;

import org.xmpp.jnodes.nio.PublicIPResolver;

import java.net.InetSocketAddress;

public class RelayPublicMask {

    private final RelayChannel channel;

    private InetSocketAddress addressA, addressB, addressA_, addressB_;

    public RelayPublicMask(final RelayChannel channel) {
        this.channel = channel;
    }

    public void discover(final String stunServer, final int port) {
        addressA = PublicIPResolver.getPublicAddress(channel.getChannelA(), stunServer, port);
        addressA_ = PublicIPResolver.getPublicAddress(channel.getChannelA_(), stunServer, port);
        addressB = PublicIPResolver.getPublicAddress(channel.getChannelB(), stunServer, port);
        addressB_ = PublicIPResolver.getPublicAddress(channel.getChannelB_(), stunServer, port);
    }

    public InetSocketAddress getAddressA() {
        return addressA;
    }

    public InetSocketAddress getAddressB() {
        return addressB;
    }

    public InetSocketAddress getAddressA_() {
        return addressA_;
    }

    public InetSocketAddress getAddressB_() {
        return addressB_;
    }
}
