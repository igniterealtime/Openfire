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

package org.jivesoftware.messenger;

import org.dom4j.Element;

/**
 * <p>Provides a common implementation of IQ packet creation, read, and update.</p>
 * <p>Info-Query (iq) is used for practically all client-server interactions and drives
 * all standard extension protocols. IQ itself is a generic envelope and protocol
 * used to transport specific protocol XML fragments.</p>
 * <h3>Warning</h3>
 * <p>Because IQ relies on the sub-element for information regarding  what IQ protocol
 * is being handled, IQ packets start parsing with the parser pointed to either the end
 * tag of the iq, or the start tag event of it's child element which ever is encountered
 * first. This allows parser handlers to advance the xpp to the sub-element to know it's
 * name and namespace but still defer to the packet for parsing.</p>
 *
 * @author Iain Shigeoka
 */
public interface IQ extends XMPPPacket {

    Type GET = new Type("get");
    Type SET = new Type("set");
    Type RESULT = new Type("result");

    /**
     * <p>Obtain the namespace of the child element.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @return The namespace of the child element
     */
    String getChildNamespace();

    /**
     * <p>Set the namespace of the child element.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @param namespace The namespace of the child element
     */
    void setChildNamespace(String namespace);

    /**
     * <p>Obtain the name of the child element.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @return The name of the child element
     */
    String getChildName();

    /**
     * <p>Set the name of the child element.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @param name The name of the child element
     */
    void setChildName(String name);

    /**
     * <p>Returns the IQ packet's only child fragment (excluding the optional error
     * sub packet).</p>
     * <p>The XMPP spec limits IQ packets to having only one child fragment so this
     * is a convenience method to return that fragment rather than having to grab it
     * from the general fragments list.</p>
     *
     * @return The child fragment for this IQ or null if there is none.
     */
    XMPPFragment getChildFragment();

    /**
     * <p>Set the child element for the IQ packet.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @param fragment The fragment to be used as the iq child element
     */
    void setChildFragment(XMPPFragment fragment);

    /**
     * <p>Obtain an IQ result packet for the current IQ using the given body.</p>
     *
     * @param body The new child element to use
     * @return The created result
     */
    IQ createResult(Element body);

    /**
     * <p>Obtain the IQ result packet for the current IQ.</p>
     * <p/>
     * <p>IQ packets may only contain one child sub-element (other than the error sub-element).</p>
     *
     * @return The namespace of the child element
     */
    IQ createResult();
}
