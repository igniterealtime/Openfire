package org.jivesoftware.openfire.pubsub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PubSubServiceInfo
{
	private PubSubService pubSubService;

	public PubSubServiceInfo( PubSubService pubSubService ) {
	    if ( pubSubService == null )
        {
            throw new IllegalArgumentException( "Argument 'pubSubService' cannot be null." );
        }
        this.pubSubService = pubSubService;
	}

	public Collection<Node> getNodes() {
		return pubSubService.getNodes();
	}

	public Node getNode(String nodeID) {
		return pubSubService.getNode(nodeID);
	}

	public List<Node> getLeafNodes() {
		List<Node> leafNodes = new ArrayList<Node>();
		for(Node node : pubSubService.getNodes()) {
			if ( ! node.isCollectionNode() ) {
				leafNodes.add(node);
			}
		}
		return leafNodes;
	}

	public CollectionNode getRootCollectionNode() {
		return pubSubService.getRootCollectionNode();
	}

	public String getServiceID() {
		return pubSubService.getServiceID();
	}

}
