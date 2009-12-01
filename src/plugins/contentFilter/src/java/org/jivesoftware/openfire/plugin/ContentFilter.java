/**
 * $RCSfile$
 * $Revision: 1594 $
 * $Date: 2005-07-04 18:08:42 +0100 (Mon, 04 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Element;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * Filters message content using regular expressions. If a content mask is
 * provided message content will be altered.
 *
 * @author Conor Hayes
 */
public class ContentFilter {

    private String patterns;

    private Collection<Pattern> compiledPatterns = new ArrayList<Pattern>();

    private String mask;


    /**
     * A default instance will allow all message content.
     *
     * @see #setPatterns(String)
     * @see #setMask(String)
     */
    public ContentFilter() {
    }

    /**
     * Set the patterns to use for searching content.
     *
     * @param regExps a comma separated String of regular expressions
     */
    public void setPatterns(String patterns) {
        if (patterns != null) {
            this.patterns = patterns;
            String[] data = patterns.split(",");

            compiledPatterns.clear();

            for (int i = 0; i < data.length; i++) {
                compiledPatterns.add(Pattern.compile(data[i]));
            }
        }
        else {
            clearPatterns();
        }

    }

    public String getPatterns() {
        return this.patterns;
    }

    /**
     * Clears all patterns. Calling this method means that all message content
     * will be allowed.
     */
    public void clearPatterns() {
        patterns = null;
        compiledPatterns.clear();
    }

    /**
     * Set the content replacement mask.
     *
     * @param mask the mask to use when replacing content
     */
    public void setMask(String mask) {
        this.mask = mask;
    }

    /**
     * @return the current mask or null if none has been set
     */
    public String getMask() {
        return mask;
    }

    /**
     * Clears the content mask.
     *
     * @see #filter(Message)
     */
    public void clearMask() {
        mask = null;
    }


    /**
     * @return true if the filter is currently masking content, false otherwise
     */
    public boolean isMaskingContent() {
        return mask != null;
    }
    
    /**
     * Filters packet content.
     *
     * @param packet the packet to filter, its content may be altered if there
     *            are content matches and a content mask is set
     * @return true if the msg content matched up, false otherwise
     */
    public boolean filter(Packet p) {        
        return process(p.getElement());
    }

    private boolean process(Element element) {
        
        boolean matched = mask(element);
        
        if (!matched || isMaskingContent())
        {
            //only check children if no match has yet been found            
            //or all content must be masked
            Iterator<Element> iter = element.elementIterator();
            while (iter.hasNext()) {
                matched |= process(iter.next());
            }
        }
        
        return matched;
    }
    
    private boolean mask(Element element) {
        
        boolean match = false;
        
        String content = element.getText();
        
        if ((content != null) && (content.length() > 0)) {
            
            for (Pattern pattern : compiledPatterns) {                
                
                Matcher matcher = pattern.matcher(content);
                
                if (matcher.find()) {
                    
                    match = true;
                    
                    if (isMaskingContent()) {
                        content = matcher.replaceAll(mask);
                        element.setText(content);
                    }
                }  
            }    
        }
        
        return match;
    }
}