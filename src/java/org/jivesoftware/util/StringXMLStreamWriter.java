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

import java.io.StringWriter;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * <p>A convenience for serializing XML to a String similar to StringWriter.</p>
 *
 * @author Iain Shigeoka
 */
public class StringXMLStreamWriter implements XMLStreamWriter {

    private XMLStreamWriter ser;
    private StringWriter writer;

    public StringXMLStreamWriter() {
        writer = new StringWriter();
        try {
            ser = XMLOutputFactory.newInstance().createXMLStreamWriter(writer);
        }
        catch (Exception e) {
            //
        }
    }

    public void setPrefix(String prefix, String namespace) throws
            IllegalArgumentException, IllegalStateException, XMLStreamException {
        ser.setPrefix(prefix, namespace);
    }

    public void setDefaultNamespace(String name) throws XMLStreamException {
        ser.setDefaultNamespace(name);
    }

    public void setNamespaceContext(NamespaceContext namespaceContext) throws XMLStreamException {
        ser.setNamespaceContext(namespaceContext);
    }

    public NamespaceContext getNamespaceContext() {
        return ser.getNamespaceContext();
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return ser.getProperty(name);
    }

    public void writeStartElement(String name) throws XMLStreamException {
        ser.writeStartElement(name);
    }

    public void writeStartElement(String name, String name1) throws XMLStreamException {
        ser.writeStartElement(name, name1);

    }

    public void writeStartElement(String name, String name1, String name2) throws XMLStreamException {
        ser.writeStartElement(name, name1, name2);
    }

    public void writeEmptyElement(String name, String name1) throws XMLStreamException {
        ser.writeEmptyElement(name, name1);
    }

    public void writeEmptyElement(String name, String name1, String name2) throws XMLStreamException {
        ser.writeEmptyElement(name, name1, name2);
    }

    public void writeEmptyElement(String name) throws XMLStreamException {
        ser.writeEmptyElement(name);
    }

    public void writeEndElement() throws XMLStreamException {
        ser.writeEndElement();
    }

    public void writeEndDocument() throws XMLStreamException {
        ser.writeEndDocument();
    }

    public void close() throws XMLStreamException {
        ser.close();
    }

    public void flush() throws XMLStreamException {
        ser.flush();
    }

    public void writeAttribute(String name, String name1) throws XMLStreamException {
        ser.writeAttribute(name, name1);
    }

    public void writeAttribute(String name, String name1, String name2, String name3) throws XMLStreamException {
        ser.writeAttribute(name, name1, name2, name3);
    }

    public void writeAttribute(String name, String name1, String name2) throws XMLStreamException {
        ser.writeAttribute(name, name1, name2);
    }

    public void writeNamespace(String name, String name1) throws XMLStreamException {
        ser.writeAttribute(name, name1);
    }

    public void writeDefaultNamespace(String name) throws XMLStreamException {
        ser.writeDefaultNamespace(name);
    }

    public void writeComment(String name) throws XMLStreamException {
        ser.writeComment(name);
    }

    public void writeProcessingInstruction(String name) throws XMLStreamException {
        ser.writeProcessingInstruction(name);
    }

    public void writeProcessingInstruction(String name, String name1) throws XMLStreamException {
        ser.writeProcessingInstruction(name, name1);
    }

    public void writeCData(String name) throws XMLStreamException {
        ser.writeCData(name);
    }

    public void writeDTD(String name) throws XMLStreamException {
        ser.writeDTD(name);
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        ser.writeEntityRef(name);
    }

    public void writeStartDocument() throws XMLStreamException {
        ser.writeStartDocument();
    }

    public void writeStartDocument(String name) throws XMLStreamException {
        ser.writeStartDocument(name);
    }

    public void writeStartDocument(String name, String name1) throws XMLStreamException {
        ser.writeStartDocument(name, name1);
    }

    public void writeCharacters(String name) throws XMLStreamException {
        ser.writeCharacters(name);
    }

    public void writeCharacters(char[] chars, int i, int i1) throws XMLStreamException {
        ser.writeCharacters(chars, i, i1);
    }

    public String getPrefix(String name) throws XMLStreamException {
        return ser.getPrefix(name);
    }

    public String toString() {
        return writer.toString();
    }
}
