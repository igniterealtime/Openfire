/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

import java.io.Reader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.dom4j.*;


/**
 * Creates a document from the current XPP position. This is specialized
 * for Jabber packets allowing a depth limit.
 * We terminate the Document when we reach the closing tag of a subelement
 * that's the depth rather than the end of the real document. This lets us
 * extract subdocuments from a running XML stream.
 *
 * @author Iain Shigeoka
 */
public class XPPReader {

    private static XMLInputFactory xmlFactory;

    /**
     * Empty constructor.
     */
    private XPPReader() {
    }

    public static Document parseDocument(Reader input, Class context)
            throws DocumentException, XMLStreamException {
        if (xmlFactory == null) {
            xmlFactory = XMLInputFactory.newInstance();
            xmlFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        }
        XMLStreamReader xpp = xmlFactory.createXMLStreamReader(input);
        Document document = DocumentHelper.createDocument();
        for (int event = xpp.next(); event != XMLStreamConstants.START_ELEMENT; event = xpp.next()) {
            if (event == XMLStreamConstants.COMMENT) {
                document.addComment(xpp.getText());
            }
        }
        document = parseDocument(xpp, document);
        return document;
    }

    public static Document parseDocument(XMLStreamReader input)
            throws DocumentException, XMLStreamException {
        Document document = DocumentHelper.createDocument();
        document = parseDocument(input, document);
        return document;
    }

    private static Document parseDocument(XMLStreamReader xpp,
                                          Document document) throws
            DocumentException, XMLStreamException {
        Branch parent = document;
        int depth = 0;
        int type = xpp.getEventType();
        for (boolean endFound = false; ; type = xpp.next()) {
            switch (type) {
                case XMLStreamConstants.END_DOCUMENT:
                    throw new DocumentException("End of document");
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    Element newElement;
                    if (xpp.getNamespaceURI().equals("jabber:client")) {
                        newElement = DocumentHelper.createElement(xpp.getLocalName());
                    }
                    else {
                        newElement = DocumentHelper.createElement(QName.get(xpp.getLocalName(),
                                xpp.getNamespaceURI()));
                    }
                    for (int i = 0; i < xpp.getAttributeCount(); i++) {
                        newElement.addAttribute(xpp.getAttributeLocalName(i),
                                xpp.getAttributeValue(i));
                    }
                    parent.add(newElement);
                    parent = newElement;
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    if (parent != null) {
                        parent = parent.getParent();
                    }
                    if (depth <= 0) {
                        endFound = true;
                    }
                    break;
                case XMLStreamConstants.CDATA:
                    String cdata = xpp.getText();
                    if (parent != null) {
                        parent.add(DocumentHelper.createCDATA(cdata));
                    }
                    else {
                        throw new DocumentException(LocaleUtils.getLocalizedString("admin.error.packet.text"));
                    }
                    break;
                case XMLStreamConstants.CHARACTERS:
                    String chars = xpp.getText();
                    if (parent != null) {
                        parent.add(DocumentHelper.createText(chars));
                    }
                    else {
                        throw new DocumentException(LocaleUtils.getLocalizedString("admin.error.packet.text"));
                    }
                    break;
                case XMLStreamConstants.COMMENT:
                    parent.add(DocumentHelper.createComment(xpp.getText()));
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE:
                    Log.warn("Unexpected entity reference event occurred " + xpp.getText());
                    break;
                default:
                    Log.warn("Unknown XML event occurred " + type);
            }
            if (endFound) {
                break;
            }
        }
        return document;
    }
}
