package org.jivesoftware.util;

import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.gateway.TransportType;
import org.xmpp.packet.JID;
import org.jivesoftware.wildfire.gateway.protocols.msn.MSNTransport;

public class MSNTransportTest
{

	/**
	 * @param args Arguments passed to program.
	 */
	public static void main(String[] args)
	{
		if (args.length!=4)
		{
			System.out.println("Syntax: java MSNTransportTest user password nickname jid");
			System.exit(0);
		}
		Log.setDebugEnabled(true);
		JID jid = new JID(args[3]);
		Registration registration = new Registration(jid, TransportType.msn, args[0], args[1], args[2], true);
		MSNTransport transport = new MSNTransport();
		transport.jid = jid;
		transport.setup(TransportType.msn, "MSN");
		TransportSession session = transport.registrationLoggedIn(registration,jid,PresenceType.available,"online",new Integer(1));
        transport.registrationLoggedOut(session);
    }

}
