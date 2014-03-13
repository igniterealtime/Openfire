package com.rayo.core.verb;

import java.net.URI;
import javax.validation.constraints.NotNull;
import com.rayo.core.validation.Messages;
import org.xmpp.packet.*;

public class InviteCommand extends AbstractVerbCommand {

    @NotNull(message=Messages.MISSING_TO)
    private URI to;

    @NotNull(message=Messages.MISSING_FROM)
    private URI from;

    private JID muc;

    public InviteCommand(JID muc, URI from, URI to)
    {
		this.muc = muc;
        this.to = to;
        this.from = from;
	}

    public JID getMuc() {
        return muc;
    }

    public void setMuc(JID muc) {
        this.muc = muc;
    }

    public URI getTo() {
        return to;
    }

    public void setTo(URI to) {
        this.to = to;
    }

    public URI getFrom() {
        return from;
    }

    public void setFrom(URI from) {
        this.from = from;
    }
}
