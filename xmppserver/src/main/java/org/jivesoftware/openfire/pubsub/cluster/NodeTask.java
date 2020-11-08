package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubService;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

public abstract class NodeTask implements ClusterTask<Void>
{
    protected transient Node.UniqueIdentifier uniqueNodeIdentifier;
    protected String nodeId;
    protected String serviceId;

    protected NodeTask()
    {

    }

    protected NodeTask(Node node)
    {
        uniqueNodeIdentifier = node.getUniqueIdentifier();
        nodeId = node.getUniqueIdentifier().getNodeId();
        serviceId = node.getUniqueIdentifier().getServiceIdentifier().getServiceId();
    }

    public Node.UniqueIdentifier getUniqueNodeIdentifier() {
        return uniqueNodeIdentifier;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public Node getNode()
    {
        PubSubService svc = getService();

        return svc != null ? svc.getNode(nodeId) : null;
    }

    public PubSubService getService()
    {
        if (XMPPServer.getInstance().getPubSubModule().getServiceID().equals(serviceId))
            return XMPPServer.getInstance().getPubSubModule();
        else
        {
            PEPServiceManager serviceMgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
            JID service = new JID( serviceId );
            return serviceMgr.hasCachedService(service) ? serviceMgr.getPEPService(service) : null;
        }
    }

    @Override
    public Void getResult()
    {
        return null;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        ExternalizableUtil.getInstance().writeSafeUTF(out, nodeId);
        ExternalizableUtil.getInstance().writeSafeUTF(out, serviceId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        nodeId = ExternalizableUtil.getInstance().readSafeUTF(in);
        serviceId = ExternalizableUtil.getInstance().readSafeUTF(in);
        uniqueNodeIdentifier = new Node.UniqueIdentifier( serviceId, nodeId );
    }
}
