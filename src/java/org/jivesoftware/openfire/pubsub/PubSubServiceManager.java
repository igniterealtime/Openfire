package org.jivesoftware.openfire.pubsub;


import org.jivesoftware.openfire.XMPPServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class PubSubServiceManager
{

    private static final class NodeManagerContainer {
        private static final PubSubServiceManager instance = new PubSubServiceManager();
    }

    public static PubSubServiceManager getInstance() {
        return NodeManagerContainer.instance;
    }

	private PubSubService pubSubService;

	private PubSubServiceManager() {
		pubSubService = XMPPServer.getInstance().getPubSubModule();
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
