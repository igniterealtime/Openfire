/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 *
 * Heavily inspired by joscardemo of the Joust Project: http://joust.kano.net/
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import net.kano.joscar.*;
import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icon.*;
import net.kano.joscar.snaccmd.search.*;

import java.net.InetAddress;

/**
 * Represents a connection to a particular OSCAR service.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public class ServiceConnection extends BasicFlapConnection {

    protected int serviceFamily;

    public ServiceConnection(OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    public ServiceConnection(String host, int port, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(host, port, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    public ServiceConnection(InetAddress ip, int port, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(ip, port, mainSession, cookie);
        this.serviceFamily = serviceFamily;
    }

    protected void clientReady() {
        oscarSession.serviceReady(this);
        super.clientReady();
    }

    protected void handleStateChange(ClientConnEvent e) {
        //Log.debug("0x" + Integer.toHexString(serviceFamily)
        //        + " service connection state changed to " + e.getNewState()
        //        + ": " + e.getReason());

        if (e.getNewState() == ClientFlapConn.STATE_FAILED) {
            oscarSession.serviceFailed(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_CONNECTED) {
            oscarSession.serviceConnected(this);
        } else if (e.getNewState() == ClientFlapConn.STATE_NOT_CONNECTED) {
            oscarSession.serviceDied(this);
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) {
        super.handleFlapPacket(e);
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        super.handleSnacPacket(e);
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
        super.handleSnacResponse(e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            // this is all we need.
            clientReady();

        } else if (cmd instanceof InterestListCmd) {
//            InterestListCmd ilc = (InterestListCmd) cmd;
//
//            InterestInfo[] infos = ilc.getInterests();
//
//            if (infos != null) {
//                Map children = new HashMap();
//
//                for (InterestInfo info1 : infos) {
//                    if (info1.getType() == InterestInfo.TYPE_CHILD) {
//                        int parentCode = info1.getParentId();
//
//                        List interests = (List) children.get(parentCode);
//
//                        if (interests == null) {
//                            interests = new LinkedList();
//                            children.put(parentCode, interests);
//                        }
//
//                        interests.add(info1);
//                    }
//                }
//                for (InterestInfo info2 : infos) {
//                    if (info2.getType() == InterestInfo.TYPE_PARENT) {
//                        Integer id = info2.getParentId();
//                        List interests = (List) children.get(id);
//
//                        //Log.debug("- " + infos[i].getName());
//                        if (interests != null) {
//                            for (Object interest : interests) {
//                                InterestInfo info = (InterestInfo) interest;
//                                //Log.debug("  - " + info.getName());
//                            }
//                        }
//                    }
//                }
//                List toplevels = (List) children.get(0);
//                if (toplevels != null) {
//                    for (Iterator it = toplevels.iterator(); it.hasNext();) {
//                        //Log.debug("  "
//                        //        + ((InterestInfo) it.next()).getName());
//                    }
//                }
//            }

        } else if (cmd instanceof SearchResultsCmd) {
//            SearchResultsCmd src = (SearchResultsCmd) cmd;
//
//            DirInfo[] results = src.getResults();
//
//            for (DirInfo result : results) {
//                //Log.debug("result " + (i + 1) + ": " + results[i]);
//            }

        } else if (cmd instanceof IconDataCmd) {
//            IconDataCmd idc = (IconDataCmd) cmd;
//
//            String sn = idc.getScreenname();
//
//            byte[] data = idc.getIconData().toByteArray();
//            Image icon = Toolkit.getDefaultToolkit().createImage(data);
//
////            oscarSession.getUserInfo(sn).setIcon(icon);

        }
    }
}
