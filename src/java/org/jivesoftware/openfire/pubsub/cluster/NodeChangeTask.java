package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

/**
 * Base class of clustering tasks for pubsub. It simply stores/retrieves the
 * node.
 * 
 * @author Robin Collier
 * 
 */
public abstract class NodeChangeTask implements ClusterTask
{
	private String nodeId;
	transient private Node node;

	public NodeChangeTask()
	{

	}

	public NodeChangeTask(String nodeIdent)
	{
		nodeId = nodeIdent;
	}

	public NodeChangeTask(Node node)
	{
		this.node = node;
		nodeId = node.getNodeID();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		ExternalizableUtil.getInstance().writeSafeUTF(out, nodeId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		nodeId = ExternalizableUtil.getInstance().readSafeUTF(in);
	}

	public Node getNode()
	{
		if (node == null)
			node = XMPPServer.getInstance().getPubSubModule().getNode(nodeId);
		return node;
	}

	public String getNodeId()
	{
		return nodeId;
	}
}
