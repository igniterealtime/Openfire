package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

public class AffiliationTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(AffiliationTask.class);

    private JID jid;
	private NodeAffiliate.Affiliation affiliation;

	public AffiliationTask()
	{
	}

	public AffiliationTask(Node node, JID jid, NodeAffiliate.Affiliation affiliation)
	{
		super(node);
		this.jid = jid;
		this.affiliation = affiliation;
	}

	public JID getJID()
	{
		return jid;
	}

	public NodeAffiliate.Affiliation getAffilation()
	{
		return affiliation;
	}
	
	public void run() {
		log.debug("[TASK] New affiliation : {}", toString());

		Node node = getNode();
		NodeAffiliate affiliate = node.getAffiliate(jid);
		if (affiliate == null) {
        	affiliate = new NodeAffiliate(node, jid);
        	affiliate.setAffiliation(affiliation);
        	node.addAffiliate(affiliate);
		} else {
			affiliate.setAffiliation(affiliation);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		super.writeExternal(out);
		ExternalizableUtil.getInstance().writeSafeUTF(out, jid.toString());
		ExternalizableUtil.getInstance().writeSerializable(out, affiliation);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		super.readExternal(in);
		jid = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
		affiliation = (NodeAffiliate.Affiliation) ExternalizableUtil.getInstance().readSerializable(in);
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + " [(service=" + serviceId + "), (nodeId=" + nodeId + 
				"), (JID=" + jid + "),(affiliation=" + affiliation + ")]";
	}
}
