/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class FragmentProxy implements XMPPFragment {

    protected XMPPFragment fragment;
    protected AuthToken authToken;
    protected Permissions permissions;

    public FragmentProxy(XMPPFragment fragment, AuthToken authToken, Permissions permissions) {
        this.fragment = fragment;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public String getNamespace() {
        return fragment.getNamespace();
    }

    public void setNamespace(String namespace) {
        fragment.setNamespace(namespace);
    }

    public String getName() {
        return fragment.getName();
    }

    public void setName(String name) {
        fragment.setName(name);
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        fragment.send(xmlSerializer, version);
    }

    public XMPPFragment createDeepCopy() {
        return fragment.createDeepCopy();
    }

    public void addFragment(XMPPFragment fragment) {
        fragment.addFragment(fragment);
    }

    public Iterator getFragments() {
        return fragment.getFragments();
    }

    public XMPPFragment getFragment(String name, String namespace) {
        return fragment.getFragment(name, namespace);
    }

    public void clearFragments() {
        fragment.clearFragments();
    }

    public int getSize() {
        return fragment.getSize();
    }
}
