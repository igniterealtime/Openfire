package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.NodeSubscription.State;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

public abstract class SubscriptionTask extends NodeTask
{
	private String subId;
	private JID owner;
	private JID subJid;
	private NodeSubscription.State state;
	transient private NodeSubscription subscription;

	public SubscriptionTask()
	{
	}

	public SubscriptionTask(NodeSubscription subscription)
	{
		super(subscription.getNode());
		subId = subscription.getID();
		state = subscription.getState();
		owner = subscription.getOwner();
		subJid = subscription.getJID();
	}

	public String getSubscriptionId()
	{
		return subId;
	}

	public JID getOwner()
	{
		return owner;
	}

	public JID getSubscriberJid()
	{
		return subJid;
	}

	public NodeSubscription.State getState()
	{
		return state;
	}

	public NodeSubscription getSubscription()
	{
		if (subscription == null)
		{
			subscription = new NodeSubscription(getNode(), owner, subJid, state, subId);
		}
		return subscription;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		super.writeExternal(out);
		ExternalizableUtil.getInstance().writeSafeUTF(out, subId);
        ExternalizableUtil.getInstance().writeSerializable(out, owner);
        ExternalizableUtil.getInstance().writeSerializable(out, subJid);
		ExternalizableUtil.getInstance().writeSerializable(out, state);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		super.readExternal(in);
		subId = ExternalizableUtil.getInstance().readSafeUTF(in);
		owner = (JID) ExternalizableUtil.getInstance().readSerializable(in);
		subJid = (JID) ExternalizableUtil.getInstance().readSerializable(in);
		state = (State) ExternalizableUtil.getInstance().readSerializable(in);
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + " [(service=" + serviceId + "), (nodeId=" + nodeId + "), (owner=" + owner
				+ "),(subscriber=" + subJid + "),(state=" + state + "),(id=" + subId + ")]";
	}
}
