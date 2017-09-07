package org.jivesoftware.openfire.pubsub;


import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class PubSubManager extends BasicModule {

    private static final class NodeManagerContainer {
        private static final PubSubManager instance = new PubSubManager();
    }

    public static PubSubManager getInstance() {
        return NodeManagerContainer.instance;
    }

	private PubSubService pubSubService;

	private PubSubManager() {
		super("PubSub Manager");
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
