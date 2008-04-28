/**
 * $RCSfile$
 * $Revision: 18940 $
 * $Date: 2005-05-23 11:13:17 -0700 (Mon, 23 May 2005) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.settings;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.openfire.fastpath.WorkgroupSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.component.ComponentManagerFactory;

public class SettingsManager {
    private final Map<String, String> settings = new HashMap<String, String>();
    private List<Element> elementList = new ArrayList<Element>();
    private WorkgroupSettings workgroupSettings;
    private Workgroup workgroup;
    private QName namespace;

    public SettingsManager() {
    }

    public void setState(Workgroup workgroup, WorkgroupSettings workgroupSettings, QName namespace) {
        this.workgroupSettings = workgroupSettings;
        this.workgroup = workgroup;
        this.namespace = namespace;

        try {
            final Element element = workgroupSettings.get(workgroup.getJID().toBareJID(), DocumentHelper.createElement(namespace));
            final List list = element.elements();

            final Iterator iter = list.iterator();
            while (iter.hasNext()) {
                Element el = (Element)iter.next();
                addToSettings(el);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);   
        }
    }


    private void addToSettings(Element elem) {
        final Iterator iter = elem.elementIterator();
        while (iter.hasNext()) {
            final Element e = (Element)iter.next();
            String name = e.getName();
            String value = e.getText();
            settings.put(name, value);
        }
        elementList.add(elem);
    }


    public Map getSettings() {
        return settings;
    }

    public void setMap(Map map) {
        Element element = DocumentHelper.createElement(namespace);
        final Iterator i = element.elementIterator();
        while (i.hasNext()) {
            element.remove((Element)i.next());
        }
        final Iterator iter = map.keySet().iterator();
        while (iter.hasNext()) {
            String key = (String)iter.next();
            String value = (String)map.get(key);

            Element elem = DocumentHelper.createElement("entry");
            elem.addElement(key).setText(value);
            element.add(elem);
        }

        try {
            workgroupSettings.add(workgroup.getJID().toBareJID(), element);
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
    }
}