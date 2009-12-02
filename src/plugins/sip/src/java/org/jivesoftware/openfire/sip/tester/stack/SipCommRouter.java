/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.sip.tester.stack;

import org.jivesoftware.openfire.sip.tester.Log;

import javax.sip.SipStack;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * <p/>
 * Title: SIP COMMUNICATOR
 * </p>
 * <p/>
 * Description:JAIN-SIP Audio/Video phone application
 * </p>
 * <p/>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p/>
 * Organisation: LSIIT laboratory (http://lsiit.u-strasbg.fr)
 * </p>
 * <p/>
 * Network Research Team (http://www-r2.u-strasbg.fr))
 * </p>
 * <p/>
 * Louis Pasteur University - Strasbourg - France
 * </p>
 *
 * @author Emil Ivov (http://www.emcho.com)
 * @version 1.1
 */
public class SipCommRouter implements Router {
    protected SipStack myStack;

    protected SipCommHop outboundProxy = null;

    protected Router stackRouter = null;

    public SipCommRouter(SipStack sipStack, String outboundProxy) {
        outboundProxy = SIPConfig.getOutboundProxy();
        this.myStack = sipStack;
        if (outboundProxy != null && outboundProxy.length() > 0) {
            this.outboundProxy = new SipCommHop(outboundProxy);
        }
    }

    /**
     * Return the default address to forward the request to. The list is
     * organized in the following priority.
     * <p/>
     * If the outboung proxy has been specified, then it is used to construct
     * the first element of the list.
     * <p/>
     * If the requestURI refers directly to a host, the host and port
     * information are extracted from it and made the next hop on the list.
     *
     * @param sipRequest is the sip request to route.
     */
    public ListIterator<Hop> getNextHops(Request sipRequest) {

        URI requestURI = sipRequest.getRequestURI();
        if (requestURI == null) {
            throw new IllegalArgumentException("Bad message: Null requestURI");
        }
        LinkedList<Hop> hops = new LinkedList<Hop>();
        if (outboundProxy != null) {
            hops.add(outboundProxy);
        }
        ListIterator routes = sipRequest.getHeaders(RouteHeader.NAME);
        if (routes != null && routes.hasNext()) {
            while (routes.hasNext()) {
                RouteHeader route = (RouteHeader)routes.next();
                SipURI uri = (SipURI)route.getAddress().getURI();
                int port = uri.getPort();
                port = (port == -1) ? 5060 : port;
                String host = uri.getHost();
                Log.debug("getNextHops", host);
                String transport = uri.getTransportParam();
                if (transport == null) {
                    transport = "udp";
                }
                Hop hop = new SipCommHop(host + ':' + port + '/' + transport);
                hops.add(hop);
            }
        }
        else if (requestURI instanceof SipURI
                && ((SipURI)requestURI).getMAddrParam() != null) {
            SipURI sipURI = ((SipURI)requestURI);
            String maddr = sipURI.getMAddrParam();
            String transport = sipURI.getTransportParam();
            if (transport == null) {
                transport = "udp";
            }
            int port = 5060;
            Hop hop = new SipCommHop(maddr, port, transport);
            hops.add(hop);
        }
        else if (requestURI instanceof SipURI) {
            SipURI sipURI = ((SipURI)requestURI);
            int port = sipURI.getPort();
            if (port == -1) {
                port = 5060;
            }
            String host = sipURI.getHost();
            String transport = sipURI.getTransportParam();
            if (transport == null) {
                transport = "UDP";
            }
            Hop hop = new SipCommHop(host + ":" + port + "/" + transport);
            hops.add(hop);
        }
        else {
            throw new IllegalArgumentException("Malformed requestURI");
        }
        return (hops.size() == 0) ? null : hops.listIterator();
    }

    /**
     * @return Returns the outboundProxy.
     */
    public Hop getOutboundProxy() {
        return this.outboundProxy;
    }

    protected void setOutboundProxy(String proxy) {
        if (SIPConfig.getOutboundProxy() != null
                && SIPConfig.getOutboundProxy().length() > 0) {
            this.outboundProxy = new SipCommHop(proxy);
        }
    }

}
