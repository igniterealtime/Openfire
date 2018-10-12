/*
 * Copyright (C) 2015 Tom Evans. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.net.MXParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class XMPPPPacketReaderFactory extends BasePooledObjectFactory<XMPPPacketReader> {

    private static Logger Log = LoggerFactory.getLogger( XMPPPPacketReaderFactory.class );

    private static XmlPullParserFactory xppFactory = null;
    static {
        try {
            xppFactory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            xppFactory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    //-- BasePooledObjectFactory implementation 
    
    @Override
    public XMPPPacketReader create() throws Exception {
        XMPPPacketReader parser = new XMPPPacketReader();
        parser.setXPPFactory( xppFactory );
        return parser;
    }

    @Override
    public PooledObject<XMPPPacketReader> wrap(XMPPPacketReader reader) {
        return new DefaultPooledObject<XMPPPacketReader>(reader);
    }

    @Override
    public boolean validateObject(PooledObject<XMPPPacketReader> po) {
        // reset the input for the pooled parser
        try {
            po.getObject().getXPPParser().resetInput();
            return true;
        } catch (XmlPullParserException xppe) {
            Log.error("Failed to reset pooled parser; evicting from pool", xppe);
            return false;
        }
    }


}
