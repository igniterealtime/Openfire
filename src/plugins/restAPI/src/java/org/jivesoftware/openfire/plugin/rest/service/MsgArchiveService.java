package org.jivesoftware.openfire.plugin.rest.service;

import org.jivesoftware.openfire.plugin.rest.controller.MsgArchiveController;
import org.jivesoftware.openfire.plugin.rest.entity.MsgArchiveEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.xmpp.packet.JID;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("restapi/v1/archive/messages/unread/{jid}")
public class MsgArchiveService {

    private MsgArchiveController archive;

    @PostConstruct
    public void init() {
        archive = MsgArchiveController.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public MsgArchiveEntity getUnReadMessagesCount(@PathParam("jid") String jidStr) throws ServiceException {
        JID jid = new JID(jidStr);
        int msgCount = archive.getUnReadMessagesCount(jid);
        return new MsgArchiveEntity(jidStr, msgCount);
    }
}
