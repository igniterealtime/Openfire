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

import java.util.*;

abstract public class AbstractFragment implements XMPPFragment {

    protected LinkedList fragments;
    protected String namespace = "";
    protected String name = "";

    /**
     * <p>Returns a namespace associated with this meta-data or null if none has been associated.</p>
     *
     * @return The namespace associated with this meta-data
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * <p>Sets a namespace associated with this meta-data or null if none has been associated.</p>
     *
     * @param namespace The namespace associated with this meta-data
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * <p>Returns a name associated with this meta-data or null if none has been associated.</p>
     *
     * @return The name associated with this meta-data
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Sets a namespace associated with this meta-data or null if none has been associated.</p>
     *
     * @param name The namespace associated with this meta-data
     */
    public void setName(String name) {
        this.name = name;
    }

    public int getSize() {
        // estimate it to be something smaller than a packet
        return 20;
    }

    public void addFragment(XMPPFragment fragment) {
        if (fragments == null) {
            fragments = new LinkedList();
        }
        else {
            // inspect for circular parent-child relationship
            if (fragment.equals(this)) {
                throw new IllegalArgumentException("Circular parent-child relationship");
            }
            Iterator frags = fragment.getFragments();
            while (frags.hasNext()) {
                if (frags.next().equals(this)) {
                    throw new IllegalArgumentException("Circular parent-child relationship");
                }
            }
        }
        fragments.addLast(fragment);
    }

    public Iterator getFragments() {
        if (fragments == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        else {
            return fragments.iterator();
        }
    }

    public XMPPFragment getFragment(String name, String namespace) {
        if (fragments == null) {
            return null;
        }
        XMPPFragment frag;
        for (Iterator frags = fragments.iterator(); frags.hasNext();) {
            frag = (XMPPFragment)frags.next();
            if (name.equals(frag.getName()) && namespace.equals(frag.getNamespace())) {
                return frag;
            }
        }
        return null;
    }

    public void clearFragments() {
        if (fragments != null) {
            fragments.clear();
        }
    }
}
