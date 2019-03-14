package org.jivesoftware.openfire.component;

import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class RequestComponentInfoNotification implements ClusterTask<Void>
{
    private JID component;
    private NodeID requestee;

    public RequestComponentInfoNotification() {
    }

    public RequestComponentInfoNotification(final JID component, NodeID requestee)
    {
        this.component = component;
        this.requestee = requestee;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        final InternalComponentManager manager = InternalComponentManager.getInstance();
        final IQ componentInfo = manager.getComponentInfo( component );
        if ( componentInfo != null )
        {
            CacheFactory.doClusterTask( new NotifyComponentInfo( componentInfo ), requestee.toByteArray() );
        }
    }

    @Override
    public void writeExternal( ObjectOutput out) throws IOException
    {
        ExternalizableUtil.getInstance().writeSerializable( out, component );
        ExternalizableUtil.getInstance().writeSerializable( out, requestee );
    }

    @Override
    public void readExternal( ObjectInput in) throws IOException, ClassNotFoundException {
        component = (JID) ExternalizableUtil.getInstance().readSerializable( in );
        requestee = (NodeID) ExternalizableUtil.getInstance().readSerializable( in );
    }
}
