/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.oscar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.net.ClientConnEvent;
import net.kano.joscar.net.ConnDescriptor;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snaccmd.conn.ClientVersionsCmd;
import net.kano.joscar.snaccmd.conn.ConnCommand;
import net.kano.joscar.snaccmd.conn.RateInfoRequest;
import net.kano.joscar.snaccmd.conn.ServerReadyCmd;
import net.kano.joscar.snaccmd.conn.SnacFamilyInfo;
import net.kano.joscar.snaccmd.mailcheck.ActivateMailCmd;
import net.kano.joscar.snaccmd.mailcheck.MailCheckCmd;
import net.kano.joscar.snaccmd.mailcheck.MailStatusRequest;
import net.kano.joscar.snaccmd.mailcheck.MailUpdate;

import org.apache.log4j.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.Message;

/**
 * @author Daniel Henninger
 */
public class EmailConnection extends ServiceConnection {

    static Logger Log = Logger.getLogger(EmailConnection.class);

    private Integer lastMailCount = 0;

    public EmailConnection(ConnDescriptor cd, OSCARSession mainSession, ByteBlock cookie, int serviceFamily) {
        super(cd, mainSession, cookie, serviceFamily);
        this.serviceFamily = serviceFamily;
    }

    @Override
    protected void handleStateChange(ClientConnEvent e) {
        Log.debug("OSCAR email service state change from "+e.getOldState()+" to "+e.getNewState());
    }

    @Override
    protected void handleFlapPacket(FlapPacketEvent e) {
//        Log.debug("OSCAR email flap packet received: "+e);
        super.handleFlapPacket(e);
    }

    @Override
    protected void clientReady() {
        super.clientReady();
        request(new MailStatusRequest());
        request(new ActivateMailCmd());
        startKeepAlive();
    }

    @Override
    protected void handleSnacPacket(SnacPacketEvent e) {
//        Log.debug("OSCAR email snac packet received: "+e);

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof ServerReadyCmd) {
            ServerReadyCmd src = (ServerReadyCmd) cmd;
            setSnacFamilies(src.getSnacFamilies());

            Collection<SnacFamilyInfo> familyInfos = new ArrayList<SnacFamilyInfo>();
            familyInfos.add(ConnCommand.FAMILY_INFO);
            familyInfos.add(MailCheckCmd.FAMILY_INFO);
            setSnacFamilyInfos(familyInfos);

            getMainSession().registerSnacFamilies(this);

            request(new ClientVersionsCmd(familyInfos));
            request(new RateInfoRequest());
        }
        else if (cmd instanceof MailUpdate) {
            MailUpdate mu = (MailUpdate)cmd;
            if (JiveGlobals.getBooleanProperty("plugin.gateway."+getMainSession().getTransport().getType()+".mailnotifications", true) && lastMailCount < mu.getUnreadCount()) {
                Integer diff = mu.getUnreadCount() - lastMailCount;
                if (diff > 0) {
                    getMainSession().getTransport().sendMessage(
                            getMainSession().getJID(),
                            getMainSession().getTransport().getJID(),
                            LocaleUtils.getLocalizedString("gateway.oscar.mail", "kraken", Arrays.asList(Integer.toString(diff), mu.getDomain(), mu.getUrl())),
                            Message.Type.headline
                    );
                }
            }
            lastMailCount = mu.getUnreadCount();
        }
        else {
            super.handleSnacPacket(e);
        }
    }

}
