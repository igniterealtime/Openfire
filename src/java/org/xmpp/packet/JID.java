/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.packet;

import org.jivesoftware.stringprep.IDNA;
import org.jivesoftware.stringprep.Stringprep;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An XMPP address (JID). A JID is made up of a node (generally a username), a domain,
 * and a resource. The node and resource are optional; domain is required. In simple
 * ABNF form:
 *
 * <ul><tt>jid = [ node "@" ] domain [ "/" resource ]</tt></ul>
 *
 * Some sample JID's:
 * <ul>
 *      <li><tt>user@example.com</tt></li>
 *      <li><tt>user@example.com/home</tt></li>
 *      <li><tt>example.com</tt></li>
 * </ul>
 *
 * Each allowable portion of a JID (node, domain, and resource) must not be more
 * than 1023 bytes in length, resulting in a maximum total size (including the '@'
 * and '/' separators) of 3071 bytes.
 *
 * @author Matt Tucker
 */
public class JID implements Comparable, Serializable, Externalizable {

    // Stringprep operations are very expensive. Therefore, we cache node, domain and
    // resource values that have already had stringprep applied so that we can check
    // incoming values against the cache.
    private static Map stringprepCache = Collections.synchronizedMap(new Cache(10000));

    private String node;
    private String domain;
    private String resource;

    private String cachedFullJID;
    private String cachedBareJID;

    /**
     * Escapes the node portion of a JID according to "JID Escaping" (JEP-0106).
     * Escaping replaces characters prohibited by node-prep with escape sequences,
     * as follows:<p>
     *
     * <table border="1">
     * <tr><td><b>Unescaped Character</b></td><td><b>Encoded Sequence</b></td></tr>
     * <tr><td>&lt;space&gt;</td><td>\20</td></tr>
     * <tr><td>"</td><td>\22</td></tr>
     * <tr><td>&</td><td>\26</td></tr>
     * <tr><td>'</td><td>\27</td></tr>
     * <tr><td>/</td><td>\2f</td></tr>
     * <tr><td>:</td><td>\3a</td></tr>
     * <tr><td>&lt;</td><td>\3c</td></tr>
     * <tr><td>&gt;</td><td>\3e</td></tr>
     * <tr><td>@</td><td>\40</td></tr>
     * <tr><td>\</td><td>\5c</td></tr>
     * </table><p>
     *
     * This process is useful when the node comes from an external source that doesn't
     * conform to nodeprep. For example, a username in LDAP may be "Joe Smith". Because
     * the &lt;space&gt; character isn't a valid part of a node, the username should
     * be escaped to "Joe\20Smith" before being made into a JID (e.g. "joe\20smith@example.com"
     * after case-folding, etc. has been applied).<p>
     *
     * All node escaping and un-escaping must be performed manually at the appropriate
     * time; the JID class will not escape or un-escape automatically.
     *
     * @param node the node.
     * @return the escaped version of the node.
     */
    public static String escapeNode(String node) {
        if (node == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder(node.length() + 8);
        for (int i=0, n=node.length(); i<n; i++) {
            char c = node.charAt(i);
            switch (c) {
                case '"': buf.append("\\22"); break;
                case '&': buf.append("\\26"); break;
                case '\'': buf.append("\\27"); break;
                case '/': buf.append("\\2f"); break;
                case ':': buf.append("\\3a"); break;
                case '<': buf.append("\\3c"); break;
                case '>': buf.append("\\3e"); break;
                case '@': buf.append("\\40"); break;
                case '\\': buf.append("\\5c"); break;
                default: {
                    if (Character.isWhitespace(c)) {
                        buf.append("\\20");
                    }
                    else {
                        buf.append(c);
                    }
                }
            }
        }
        return buf.toString();
    }

    /**
     * Un-escapes the node portion of a JID according to "JID Escaping" (JEP-0106).<p>
     * Escaping replaces characters prohibited by node-prep with escape sequences,
     * as follows:<p>
     *
     * <table border="1">
     * <tr><td><b>Unescaped Character</b></td><td><b>Encoded Sequence</b></td></tr>
     * <tr><td>&lt;space&gt;</td><td>\20</td></tr>
     * <tr><td>"</td><td>\22</td></tr>
     * <tr><td>&</td><td>\26</td></tr>
     * <tr><td>'</td><td>\27</td></tr>
     * <tr><td>/</td><td>\2f</td></tr>
     * <tr><td>:</td><td>\3a</td></tr>
     * <tr><td>&lt;</td><td>\3c</td></tr>
     * <tr><td>&gt;</td><td>\3e</td></tr>
     * <tr><td>@</td><td>\40</td></tr>
     * <tr><td>\</td><td>\5c</td></tr>
     * </table><p>
     *
     * This process is useful when the node comes from an external source that doesn't
     * conform to nodeprep. For example, a username in LDAP may be "Joe Smith". Because
     * the &lt;space&gt; character isn't a valid part of a node, the username should
     * be escaped to "Joe\20Smith" before being made into a JID (e.g. "joe\20smith@example.com"
     * after case-folding, etc. has been applied).<p>
     *
     * All node escaping and un-escaping must be performed manually at the appropriate
     * time; the JID class will not escape or un-escape automatically.
     *
     * @param node the escaped version of the node.
     * @return the un-escaped version of the node.
     */
    public static String unescapeNode(String node) {
        if (node == null) {
            return null;
        }
        char [] nodeChars = node.toCharArray();
        StringBuilder buf = new StringBuilder(nodeChars.length);
        for (int i=0, n=nodeChars.length; i<n; i++) {
            compare: {
                char c = node.charAt(i);
                if (c == '\\' && i+2<n) {
                    char c2 = nodeChars[i+1];
                    char c3 = nodeChars[i+2];
                    if (c2 == '2') {
                        switch (c3) {
                            case '0': buf.append(' '); i+=2; break compare;
                            case '2': buf.append('"'); i+=2; break compare;
                            case '6': buf.append('&'); i+=2; break compare;
                            case '7': buf.append('\''); i+=2; break compare;
                            case 'f': buf.append('/'); i+=2; break compare;
                        }
                    }
                    else if (c2 == '3') {
                        switch (c3) {
                            case 'a': buf.append(':'); i+=2; break compare;
                            case 'c': buf.append('<'); i+=2; break compare;
                            case 'e': buf.append('>'); i+=2; break compare;
                        }
                    }
                    else if (c2 == '4') {
                        if (c3 == '0') {
                            buf.append("@");
                            i+=2;
                            break compare;
                        }
                    }
                    else if (c2 == '5') {
                        if (c3 == 'c') {
                            buf.append("\\");
                            i+=2;
                            break compare;
                        }
                    }
                }
                buf.append(c);
            }
        }
        return buf.toString();
    }

    public static String resourceprep(String resource) throws StringprepException {
        String answer = resource;
        if (!stringprepCache.containsKey(resource)) {
            answer = Stringprep.resourceprep(resource);
            // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
            if (answer != null && answer.length()*2 > 1023) {
                return answer;
            }
            stringprepCache.put(answer, null);
        }
        return answer;
    }

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public JID() {
    }

    /**
     * Constructs a JID from it's String representation.
     *
     * @param jid a valid JID.
     * @throws IllegalArgumentException if the JID is not valid.
     */
    public JID(String jid) {
        if (jid == null) {
            throw new NullPointerException("JID cannot be null");
        }
        String[] parts = getParts(jid);

        init(parts[0], parts[1], parts[2]);
    }

    /**
     * Constructs a JID given a node, domain, and resource.
     *
     * @param node the node.
     * @param domain the domain, which must not be <tt>null</tt>.
     * @param resource the resource.
     * @throws IllegalArgumentException if the JID is not valid.
     */
    public JID(String node, String domain, String resource) {
        if (domain == null) {
            throw new NullPointerException("Domain cannot be null");
        }
        init(node, domain, resource);
    }

    /**
     * Constructs a JID given a node, domain, and resource being able to specify if stringprep
     * should be applied or not.
     *
     * @param node the node.
     * @param domain the domain, which must not be <tt>null</tt>.
     * @param resource the resource.
     * @param skipStringprep true if stringprep should not be applied.
     * @throws IllegalArgumentException if the JID is not valid.
     */
    public JID(String node, String domain, String resource, boolean skipStringprep) {
        if (domain == null) {
            throw new NullPointerException("Domain cannot be null");
        }
        if (skipStringprep) {
            this.node = node;
            this.domain = domain;
            this.resource = resource;
            // Cache the bare and full JID String representation
            updateCache();
        }
        else {
            init(node, domain, resource);
        }
    }

    /**
     * Returns a String array with the parsed node, domain and resource.
     * No Stringprep is performed while parsing the textual representation.
     *
     * @param jid the textual JID representation.
     * @return a string array with the parsed node, domain and resource.
     */
    static String[] getParts(String jid) {
        String[] parts = new String[3];
        String node = null , domain, resource;
        if (jid == null) {
            return parts;
        }

        int atIndex = jid.indexOf("@");
        int slashIndex = jid.indexOf("/");

        // Node
        if (atIndex > 0) {
            node = jid.substring(0, atIndex);
        }

        // Domain
        if (atIndex + 1 > jid.length()) {
            throw new IllegalArgumentException("JID with empty domain not valid");
        }
        if (atIndex < 0) {
            if (slashIndex > 0) {
                domain = jid.substring(0, slashIndex);
            }
            else {
                domain = jid;
            }
        }
        else {
            if (slashIndex > 0) {
                domain = jid.substring(atIndex + 1, slashIndex);
            }
            else {
                domain = jid.substring(atIndex + 1);
            }
        }

        // Resource
        if (slashIndex + 1 > jid.length() || slashIndex < 0) {
            resource = null;
        }
        else {
            resource = jid.substring(slashIndex + 1);
        }
        parts[0] = node;
        parts[1] = domain;
        parts[2] = resource;
        return parts;
    }

    /**
     * Transforms the JID parts using the appropriate Stringprep profiles, then
     * validates them. If they are fully valid, the field values are saved, otherwise
     * an IllegalArgumentException is thrown.
     *
     * @param node the node.
     * @param domain the domain.
     * @param resource the resource.
     */
    private void init(String node, String domain, String resource) {
        // Set node and resource to null if they are the empty string.
        if (node != null && node.equals("")) {
            node = null;
        }
        if (resource != null && resource.equals("")) {
            resource = null;
        }
        // Stringprep (node prep, resourceprep, etc).
        try {
            if (!stringprepCache.containsKey(node)) {
                this.node = Stringprep.nodeprep(node);
                // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
                if (this.node != null && this.node.length()*2 > 1023) {
                    throw new IllegalArgumentException("Node cannot be larger than 1023 bytes. " +
                            "Size is " + (node.length() * 2) + " bytes.");
                }
                stringprepCache.put(this.node, null);
            }
            else {
                this.node = node;
            }
            // XMPP specifies that domains should be run through IDNA and
            // that they should be run through nameprep before doing any
            // comparisons. We always run the domain through nameprep to
            // make comparisons easier later.
            if (!stringprepCache.containsKey(domain)) {
                this.domain = Stringprep.nameprep(IDNA.toASCII(domain), false);
                // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
                if (this.domain.length()*2 > 1023) {
                    throw new IllegalArgumentException("Domain cannot be larger than 1023 bytes. " +
                            "Size is " + (this.domain.length() * 2) + " bytes.");
                }
                stringprepCache.put(this.domain, null);
            }
            else {
                this.domain = domain;
            }
            this.resource = resourceprep(resource);
            // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
            if (resource != null && resource.length()*2 > 1023) {
                throw new IllegalArgumentException("Resource cannot be larger than 1023 bytes. " +
                        "Size is " + (resource.length() * 2) + " bytes.");
            }
            // Cache the bare and full JID String representation
            updateCache();
        }
        catch (Exception e) {
            StringBuilder buf = new StringBuilder();
            if (node != null) {
                buf.append(node).append("@");
            }
            buf.append(domain);
            if (resource != null) {
                buf.append("/").append(resource);
            }
            throw new IllegalArgumentException("Illegal JID: " + buf.toString(), e);
        }
    }

    private void updateCache() {
        // Cache the bare JID
        StringBuilder buf = new StringBuilder(40);
        if (node != null) {
            buf.append(node).append("@");
        }
        buf.append(domain);
        cachedBareJID = buf.toString();

        // Cache the full JID
        if (resource != null) {
            buf.append("/").append(resource);
            cachedFullJID = buf.toString();
        }
        else {
            cachedFullJID = cachedBareJID;
        }
    }

    /**
     * Returns the node, or <tt>null</tt> if this JID does not contain node information.
     *
     * @return the node.
     */
    public String getNode() {
        return node;
    }

    /**
     * Returns the domain.
     *
     * @return the domain.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the resource, or <tt>null</tt> if this JID does not contain resource information.
     *
     * @return the resource.
     */
    public String getResource() {
        return resource;
    }

    /**
     * Returns the String representation of the bare JID, which is the JID with
     * resource information removed.
     *
     * @return the bare JID.
     */
    public String toBareJID() {
        return cachedBareJID;
    }

    /**
     * Returns a String representation of the JID.
     *
     * @return a String representation of the JID.
     */
    public String toString() {
        return cachedFullJID;
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object object) {
        if (!(object instanceof JID)) {
            return false;
        }
        if (this == object) {
            return true;
        }
        JID jid = (JID)object;
        // Node. If node isn't null, compare.
        if (node != null) {
            if (!node.equals(jid.node)) {
                return false;
            }
        }
        // Otherwise, jid.node must be null.
        else if (jid.node != null) {
            return false;
        }
        // Compare domain, which must be null.
        if (!domain.equals(jid.domain)) {
            return false;
        }
        // Resource. If resource isn't null, compare.
        if (resource != null) {
            if (!resource.equals(jid.resource)) {
                return false;
            }
        }
        // Otherwise, jid.resource must be null.
        else if (jid.resource != null) {
            return false;
        }
        // Passed all checks, so equal.
        return true;
    }

    public int compareTo(Object o) {
        if (!(o instanceof JID)) {
            throw new ClassCastException("Ojbect not instanceof JID: " + o);
        }
        JID jid = (JID)o;

        // Comparison order is domain, node, resource.
        int compare = domain.compareTo(jid.domain);
        if (compare == 0 && node != null && jid.node != null) {
            compare = node.compareTo(jid.node);
        }
        if (compare == 0 && resource != null && jid.resource != null) {
            compare = resource.compareTo(jid.resource);
        }
        return compare;
    }

    /**
     * Returns true if two JID's are equivalent. The JID components are compared using
     * the following rules:<ul>
     *      <li>Nodes are normalized using nodeprep (case insensitive).
     *      <li>Domains are normalized using IDNA and then nameprep (case insensitive).
     *      <li>Resources are normalized using resourceprep (case sensitive).</ul>
     *
     * These normalization rules ensure, for example, that
     * <tt>User@EXAMPLE.com/home</tt> is considered equal to <tt>user@example.com/home</tt>.
     *
     * @param jid1 a JID.
     * @param jid2 a JID.
     * @return true if the JIDs are equivalent; false otherwise.
     * @throws IllegalArgumentException if either JID is not valid.
     */
    public static boolean equals(String jid1, String jid2) {
        return new JID(jid1).equals(new JID(jid2));
    }

    /**
     * A simple cache class that extends LinkedHashMap. It uses an LRU policy to
     * keep the cache at a maximum size.
     */
    private static class Cache extends LinkedHashMap {

        private int maxSize;

        public Cache(int maxSize) {
            super(64, .75f, true);
            this.maxSize = maxSize;
        }

        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > maxSize;
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, toString());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        String jid = ExternalizableUtil.getInstance().readSafeUTF(in);
        String[] parts = getParts(jid);

        this.node = parts[0];
        this.domain = parts[1];
        this.resource = parts[2];
        // Cache the bare and full JID String representation
        updateCache();
    }
}