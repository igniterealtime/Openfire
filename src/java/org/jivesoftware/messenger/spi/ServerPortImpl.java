/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.ServerPort;
import java.util.ArrayList;
import java.util.Iterator;

public class ServerPortImpl implements ServerPort {

    private int port;
    private ArrayList names;
    private String address;
    private boolean secure;
    private String algorithm;

    public ServerPortImpl(int port, String name, String address, boolean isSecure, String algorithm) {
        this.port = port;
        this.names = new ArrayList(1);
        this.names.add(name);
        this.address = address;
        this.secure = isSecure;
        this.algorithm = algorithm;
    }

    public int getPort() {
        return port;
    }

    public Iterator getDomainNames() {
        return names.iterator();
    }

    public String getIPAddress() {
        return address;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getSecurityType() {
        return algorithm;
    }
}
