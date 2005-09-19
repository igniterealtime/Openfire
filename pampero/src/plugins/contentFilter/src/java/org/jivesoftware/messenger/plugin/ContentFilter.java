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

package org.jivesoftware.messenger.plugin;

import org.xmpp.packet.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Filters message content.
     *
     * @param msg the message to filter, its subject/body may be altered if there
     *            are content matches and a content mask is set
     * @return true if the msg content matched up, false otherwise
     */
    public boolean filter(Message msg) {
        boolean hasMatch = false;

        if (msg.getSubject() != null) {
            if (hasMatch(msg.getSubject())) {
                hasMatch = true;
                if (isMaskingContent()) {
                    String newSubject = replaceContent(msg.getSubject());
                    msg.setSubject(newSubject);
                }
            }
        }

        if (msg.getBody() != null) {
            if (hasMatch(msg.getBody())) {
                hasMatch = true;
                if (isMaskingContent()) {
                    String newBody = replaceContent(msg.getBody());
                    msg.setBody(newBody);
                }
            }
        }

        return hasMatch;
    }

    private String replaceContent(String content) {
        for (Pattern pattern : compiledPatterns) {
            Matcher m = pattern.matcher(content);
            content = m.replaceAll(mask);
        }

        return content;
    }

    /**
     * Performs sequential search for any pattern match.
     *
     * @param content the content to search against
     * @return true if a match is found, false otherwise
     */
    private boolean hasMatch(String content) {
        boolean hasMatch = false;

        for (Pattern pattern : compiledPatterns) {
            Matcher m = pattern.matcher(content);

            if (m.find()) {
                hasMatch = true;
                break;
            }
        }

        return hasMatch;
    }
}