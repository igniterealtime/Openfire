package org.jivesoftware.openfire.net;

import org.dom4j.Element;

/**
 * Interface for plugins that handle inline elements in SASL2 bind2 requests.
 */
public interface Bind2InlineHandler {

    /**
     * Gets the namespace this handler processes.
     *
     * @return The XML namespace URI this handler supports
     */
    String getNamespace();

    /**
     * Process an inline element from a bind2 request.
     *
     * @param bound The "bound" element to add any output to
     * @param element The DOM element to process
     * @return true if the element was handled successfully, false otherwise
     */
    boolean handleElement(Element bound, Element element);
}
