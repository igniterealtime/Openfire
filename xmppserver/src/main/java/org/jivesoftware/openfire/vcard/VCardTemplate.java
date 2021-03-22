package org.jivesoftware.openfire.vcard;

import org.dom4j.Document;
import org.dom4j.Element;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Class to hold a <code>Document</code> representation of a vcard mapping
 * and unique attribute placeholders. Used by <code>VCard</code> to apply
 * a <code>Map</code> of ldap attributes to ldap values via
 * <code>MessageFormat</code>
 *
 * @author rkelly
 */
public class VCardTemplate {

    private Document document;

    private String[] attributes;

    public VCardTemplate(Document document) {
        Set<String> set = new HashSet<>();
        this.document = document;
        treeWalk(this.document.getRootElement(), set);
        attributes = set.toArray(new String[0]);
    }

    public String[] getAttributes() {
        return attributes;
    }

    public Document getDocument() {
        return document;
    }

    private void treeWalk(Element rootElement, Set<String> set) {
        for ( final Element element : rootElement.elements() ) {
            final String value = element.getTextTrim();
            if ( value != null && !value.isEmpty()) {
                final Matcher matcher = VCard.PATTERN.matcher(value);
                while (matcher.find()) {
                    final String match = matcher.group(2);
                    set.add(match);
                }
            }
            treeWalk(element, set);
        }
    }
}
