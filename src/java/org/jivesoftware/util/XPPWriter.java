/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.jivesoftware.messenger.XMPPFragment;
import java.io.Writer;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.*;

/**
 * <p>Writes out Dom4j documents using the XPP serializer.</p>
 *
 * @author Iain Shigeoka
 */
public class XPPWriter {

    private static XMLOutputFactory xmlFactory;

    /**
     * <p>Writes out the given document.</p>
     *
     * @param doc The document to write
     * @param out The writer to write to
     */
    public static void write(Document doc, Writer out) throws XMLStreamException {
        if (xmlFactory == null) {
            xmlFactory = XMLOutputFactory.newInstance();
        }
        XMLStreamWriter serializer = xmlFactory.createXMLStreamWriter(out);
        write(doc, serializer);
    }

    /**
     * <p>Writes out the given document.</p>
     *
     * @param doc        The document to write
     * @param serializer The writer to write to
     */
    public static void write(Document doc, XMLStreamWriter serializer)
            throws XMLStreamException {

        for (int i = 0, size = doc.nodeCount(); i < size; i++) {
            Node node = doc.node(i);
            writeNode(node, serializer);
        }
        serializer.flush();
    }

    /**
     * <p>Writes out the given document.</p>
     *
     * @param element    The element to write
     * @param serializer The serializer to write to
     */
    public static void write(Element element, XMLStreamWriter serializer)
            throws XMLStreamException {

        writeElement(element, serializer, null, -1);
        serializer.flush();
    }

    /**
     * <p>Writes out the given document.</p>
     *
     * @param element    The element to write
     * @param serializer The serializer to write to
     */
    public static void write(Element element,
                             XMLStreamWriter serializer,
                             Iterator fragments,
                             int version)
            throws XMLStreamException {

        writeElement(element, serializer, fragments, version);
        serializer.flush();
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    private static void writeElement(Element element,
                                     XMLStreamWriter serializer,
                                     Iterator fragments,
                                     int version)
            throws XMLStreamException {
        int size = element.nodeCount();
        String namespace = element.getNamespace().getURI();
        NamespaceContext context = serializer.getNamespaceContext();
        if (namespace == null || "".equals(namespace)) {
            // no namespace - must assume default namespace
            serializer.writeStartElement(element.getName());
        }
        else {
            String prefix = context.getPrefix(namespace);
            if (prefix == null) {
                // Unbound prefix, we'll set to default
                serializer.writeStartElement(element.getName());
                serializer.writeDefaultNamespace(namespace);
            }
            else if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
                // Already in the default
                serializer.writeStartElement(element.getName());
            }
            else {
                // Uses a bound prefix
                serializer.writeStartElement(prefix, element.getName());
            }
        }

        writeAttributes(element, serializer);

        if (size > 0) {
            writeElementContent(element, serializer);
        }
        if (fragments != null) {
            while (fragments.hasNext()) {
                ((XMPPFragment)fragments.next()).send(serializer, version);
            }
        }
        serializer.writeEndElement();
    }

    /**
     * Outputs the content of the given element. If whitespace trimming is
     * enabled then all adjacent text nodes are appended together before
     * the whitespace trimming occurs to avoid problems with multiple
     * text nodes being created due to text content that spans parser buffers
     * in a SAX parser.
     */
    private static void writeElementContent(Element element, XMLStreamWriter serializer)
            throws XMLStreamException {
        for (int i = 0, size = element.nodeCount(); i < size; i++) {
            Node node = element.node(i);
            writeNode(node, serializer);
        }
    }

    private static void writeNode(Node node, XMLStreamWriter serializer)
            throws XMLStreamException {
        int nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ELEMENT_NODE:
                writeElement((Element)node, serializer, null, -1);
                break;
            case Node.ATTRIBUTE_NODE:
                writeAttribute((Attribute)node, serializer);
                break;
            case Node.TEXT_NODE:
                serializer.writeCharacters(node.getText());
                break;
            case Node.CDATA_SECTION_NODE:
                serializer.writeCData(node.getText());
                break;
            case Node.ENTITY_REFERENCE_NODE:
                serializer.writeEntityRef(node.getName());
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                serializer.writeProcessingInstruction(node.getText());
                break;
            case Node.COMMENT_NODE:
                serializer.writeComment(node.getText());
                break;
            case Node.DOCUMENT_NODE:
                serializer.writeStartDocument();
                break;
            case Node.DOCUMENT_TYPE_NODE:
                break;
            case Node.NAMESPACE_NODE:
                // Will be output with attributes
                //write((Namespace) node);
                break;
            default:
                throw new XMLStreamException("Invalid node type: " + node);
        }
    }


    /**
     * Writes the attributes of the given element
     */
    static private void writeAttributes(Element element, XMLStreamWriter serializer)
            throws XMLStreamException {
        for (int i = 0, size = element.attributeCount(); i < size; i++) {
            Attribute attribute = element.attribute(i);
            writeAttribute(attribute, serializer);
        }
    }

    private static void writeAttribute(Attribute attribute, XMLStreamWriter serializer)
            throws XMLStreamException {
        Namespace ns = attribute.getNamespace();
        serializer.writeAttribute(ns.getURI(), attribute.getName(), attribute.getValue());
    }
}
