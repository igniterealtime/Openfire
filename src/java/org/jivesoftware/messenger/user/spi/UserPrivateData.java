package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.messenger.PrivateStore;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

final public class UserPrivateData {
    private final Map settings = new HashMap();
    private List elementList = new ArrayList();
    private PrivateStore privateStore;
    private long ID;
    final String settingsNamespace = "jive:user:settings";
    final String settingsElementName = "personal_settings";

    final Namespace NS = Namespace.get(settingsNamespace);
    final QName namespace = DocumentHelper.createQName(settingsElementName, NS);


    public UserPrivateData() {
    }

    public void setState(long id, PrivateStore privateStore) {
        this.privateStore = privateStore;
        this.ID = id;


        try {
            final Element element = privateStore.get(this.ID, DocumentHelper.createElement(namespace));
            final List list = element.elements();

            final Iterator iter = list.iterator();
            while (iter.hasNext()) {
                Element el = (Element)iter.next();
                addToSettings(el);
            }
        }
        catch (Exception ex) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), ex);
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

    public String getProperty(String property) {
        return (String)getSettings().get(property);
    }

    public void setProperty(String name, String value) {
        getSettings().put(name, value);
    }

    public Map getSettings() {
        return settings;
    }

    public void save() {
        setMap(getSettings());
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
            String value = (String)getSettings().get(key);

            Element elem = DocumentHelper.createElement("entry");
            elem.addElement(key).setText(value);
            element.add(elem);
        }

        try {
            privateStore.add(this.ID, element);
        }
        catch (Exception ex) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), ex);
        }
    }
}