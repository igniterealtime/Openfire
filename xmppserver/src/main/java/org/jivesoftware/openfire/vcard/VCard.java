package org.jivesoftware.openfire.vcard;

import org.dom4j.Document;
import org.dom4j.Element;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * vCard class that converts vcard data using a template.
 */
public class VCard {

    /**
     * A regular expression that matches values enclosed in { and }, applying a group to the value that's surrounded.
     */
    public static final Pattern PATTERN = Pattern.compile("(\\{)([\\d\\D&&[^}]]+)(})");

    private VCardTemplate template;

    public VCard(VCardTemplate template) {
        this.template = template;
    }

    public Element getVCard(Map<String, String> map) {
        Document document = (Document) template.getDocument().clone();
        Element element = document.getRootElement();
        return treeWalk(element, map);
    }

    private Element treeWalk(Element rootElement, Map<String, String> map) {
        for ( final Element element : rootElement.elements() ) {
            String elementText = element.getTextTrim();
            if (elementText != null && !"".equals(elementText)) {
                String format = element.getStringValue();

                // A map that will hold all replacements for placeholders
                final Map<String,String> replacements = new HashMap<>();

                // find all placeholders, and look up what they should be replaced with.
                final Matcher matcher = PATTERN.matcher(format);
                while (matcher.find()) {
                    final String group = matcher.group();
                    final String attribute = matcher.group(2);
                    final String value = map.get(attribute);
                    replacements.put( group, value );
                }

                // perform the replacement.
                for ( Map.Entry<String, String> entry : replacements.entrySet() ) {
                    final String placeholder = entry.getKey();
                    final String replacement = entry.getValue() != null ? entry.getValue() : "";
                    format = format.replace(placeholder, replacement);
                }

                // When 'prioritized' replacements are used, the resulting value now will have those filled out:
                // example:   (|()(valueB)(valueC))
                // From this format, only the first non-empty value enclosed in brackets needs to be used.
                final int start = format.indexOf("(|(");
                final int end = format.indexOf("))");
                if ( start > -1 && end > start ) {
                    // Take the substring that is: (|()(valueB)(valueC))
                    final String filter = format.substring(start, end + "))".length());

                    // Take the substring that is: )(valueB)(valueC
                    final String values = filter.substring("(|(".length(), filter.length() - "))".length() );

                    // Split on ")(" to get the individual values.
                    final String[] splitted = values.split("\\)\\(");

                    // find the first non-empty string.
                    String firstValue = "";
                    for ( final String split : splitted ) {
                        if ( split != null && !split.isEmpty() ) {
                            firstValue = split;
                            break;
                        }
                    }

                    // Replace the original filter with just the first matching value.
                    format = format.replace(filter, firstValue);
                }

                element.setText(format);
            }
            treeWalk(element, map);
        }
        return rootElement;
    }
}
