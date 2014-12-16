/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;

import java.util.*;

/**
 * Operation set exposes the functionality of node capabilities discovery.
 *
 * @author Pawel Domas
 */
public interface OperationSetSimpleCaps
    extends OperationSet
{
    /**
     * Returns the list of sub-nodes of given <tt>node</tt>.
     *
     * @param node the node for which child nodes will be discovered.
     *
     * @return the list of sub-nodes of given <tt>node</tt>.
     */
    List<String> getItems(String node);

    /**
     * Check if given node supports specified feature set.
     *
     * @param node the node to be checked.
     * @param features the array of feature names to be checked.
     *
     * @return <tt>true</tt> if given node support all features specified in
     *         <tt>features</tt> array or <tt>false</tt> if at least on of
     *         the features is not supported.
     */
    boolean hasFeatureSupport(String node, String[] features);

    //boolean hasFeatureSupport(String node, String subnode, String[] features);
}
