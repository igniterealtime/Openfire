package com.reucon.openfire.plugin.archive.xep0313;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

import java.util.Date;

/**
 * Created by dwd on 26/07/16.
 */
public final class Result extends PacketExtension {
    public Result(Forwarded forwarded, String xmlns, String queryId, String id) {
        super("result", xmlns);
        element.addAttribute("queryid", queryId);
        element.addAttribute("id", id);
        element.add(forwarded.getElement());
    }
}
