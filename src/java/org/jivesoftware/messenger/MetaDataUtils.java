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

package org.jivesoftware.messenger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.dom4j.Element;

public class MetaDataUtils {
    private static final String META_DATA_NAMESPACE = "http://www.jivesoftware.com/workgroup/metadata";
    private static final String META_DATA_NAME = "metadata";

    private MetaDataUtils() {
    }

    final public static Map getMetaData(Iterator iterator) {
        final XMPPDOMFragment metaData = getInfoMetaData(iterator);
        return getMap(metaData);
    }

    private static Map getMap(XMPPDOMFragment metaData) {
        final Map map = new HashMap();
        Iterator items = metaData.getRootElement().element(META_DATA_NAME).elementIterator();
        while (items.hasNext()) {
            final Element item = (Element)items.next();
            if ("value".equals(item.getName())) {
                String name = item.attributeValue("name");
                if (name != null) {
                    String value = item.getTextTrim();
                    map.put(name, value);
                }
            }
        }
        return map;
    }


    private static XMPPDOMFragment getInfoMetaData(Iterator metaDataIter) {
        XMPPDOMFragment metaData = null;
        while (metaDataIter.hasNext()) {
            MetaDataFragment fragment = (MetaDataFragment)metaDataIter.next();
            if (META_DATA_NAME.equals(fragment.getName())
                    && META_DATA_NAMESPACE.equals(fragment.getNamespace())) {
                metaData = fragment.convertToDOMFragment();
                break;
            }
        }
        return metaData;
    }

}