/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.http;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.net.MXParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import javax.xml.XMLConstants;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Representation of the 'body' element of an HTTP-Bind defined request.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class HttpBindBody
{
    private static final Logger Log = LoggerFactory.getLogger( HttpBindBody.class );

    private static XmlPullParserFactory factory;

    static
    {
        try
        {
            factory = XmlPullParserFactory.newInstance( MXParser.class.getName(), null );
        }
        catch ( XmlPullParserException e )
        {
            Log.error( "Error creating a parser factory", e );
        }
    }

    private ThreadLocal<XMPPPacketReader> localReader = new ThreadLocal<>();

    private final Document document;

    public static HttpBindBody from( String content ) throws DocumentException, XmlPullParserException, IOException
    {
        return new HttpBindBody( content );
    }

    protected HttpBindBody( String content ) throws DocumentException, XmlPullParserException, IOException
    {
        // Parse document from the content.
        document = getPacketReader().read( new StringReader( content ), "UTF-8" );
        final Element node = document.getRootElement();
        if ( node == null || !"body".equals( node.getName() ) )
        {
            throw new IllegalArgumentException( "Root element 'body' is missing from parsed request data" );
        }
    }

    private XMPPPacketReader getPacketReader()
    {
        // Reader is associated with a new XMPPPacketReader
        XMPPPacketReader reader = localReader.get();
        if ( reader == null )
        {
            reader = new XMPPPacketReader();
            reader.setXPPFactory( factory );
            localReader.set( reader );
        }
        return reader;
    }

    public Long getRid()
    {
        final String value = document.getRootElement().attributeValue( "rid" );
        if ( value == null )
        {
            return null;
        }

        return getLongAttribute( value, -1 );
    }

    public String getSid()
    {
        return document.getRootElement().attributeValue( "sid" );
    }

    public boolean isEmpty()
    {
        return document.getRootElement().elements().isEmpty();
    }

    public boolean isPoll()
    {
        boolean isPoll = isEmpty();
        if ( "terminate".equals( document.getRootElement().attributeValue( "type" ) ) )
        { isPoll = false; }
        else if ( "true".equals( document.getRootElement().attributeValue( new QName( "restart", document.getRootElement().getNamespaceForPrefix( "xmpp" ) ) ) ) )
        { isPoll = false; }
        else if ( document.getRootElement().attributeValue( "pause" ) != null )
        { isPoll = false; }

        return isPoll;
    }

    public String getLanguage()
    {
        // Default language is English ("en").
        final String language = document.getRootElement().attributeValue( QName.get( "lang", XMLConstants.XML_NS_URI ) );
        if ( language == null || "".equals( language ) )
        {
            return "en";
        }

        return language;
    }

    public int getWait()
    {
        return getIntAttribute( document.getRootElement().attributeValue( "wait" ), 60 );
    }

    public int getHold()
    {
        return getIntAttribute( document.getRootElement().attributeValue( "hold" ), 1 );
    }

    public int getPause()
    {
        return getIntAttribute( document.getRootElement().attributeValue( "pause" ), -1 );
    }

    public String getVersion()
    {
        final String version = document.getRootElement().attributeValue( "ver" );
        if ( version == null || "".equals( version ) )
        {
            return "1.5";
        }

        return version;
    }

    public int getMajorVersion()
    {
        return Integer.parseInt( getVersion().split( "\\." )[0] );
    }

    public int getMinorVersion()
    {
        return Integer.parseInt( getVersion().split( "\\." )[1] );
    }

    public String getType()
    {
        return document.getRootElement().attributeValue( "type" );
    }

    public boolean isRestart()
    {
        final String restart = document.getRootElement().attributeValue( new QName( "restart", document.getRootElement().getNamespaceForPrefix( "xmpp" ) ) );
        return "true".equals( restart );
    }

    public List<Element> getStanzaElements()
    {
        return document.getRootElement().elements();
    }

    public String asXML()
    {
        return document.getRootElement().asXML();
    }

    @Override
    public String toString()
    {
        return asXML();
    }

    public Document getDocument()
    {
        return document;
    }

    protected static long getLongAttribute(String value, long defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    protected static int getIntAttribute(String value, int defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }
}
