/**
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.spark;

import java.util.Collection;
import java.util.Iterator;

import org.dom4j.Element;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Intercepts Bookmark Storage requests and appends all server based Bookmarks to
 * the result.
 *
 * @author Derek DeMoro
 */
public class BookmarkInterceptor implements PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(BookmarkInterceptor.class);

    /**
     * Initializes the BookmarkInterceptor and needed Server instances.
     */
    public BookmarkInterceptor() {
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        if (!processed && packet instanceof IQ && !incoming) {

            // Check for the Bookmark Storage element and hand off to the Bookmark engine.
            IQ iq = (IQ)packet;
            Element childElement = iq.getChildElement();
            if (childElement == null || iq.getType() != IQ.Type.result) {
                return;
            }

            String namespace = childElement.getNamespaceURI();
            if ("jabber:iq:private".equals(namespace)) {
                // In private data, when a user is attempting to retrieve bookmark
                // information, there will be a storage:bookmarks namespace.
                Element storageElement = childElement.element("storage");
                if (storageElement == null) {
                    return;
                }

                namespace = storageElement.getNamespaceURI();
                if ("storage:bookmarks".equals(namespace)) {
                    // Append Server defined bookmarks for user.
                    JID toJID = iq.getTo();
                    addBookmarks(toJID, storageElement);
                }
            }
        }
    }

    /**
     * Add this interceptor to the interceptor manager.
     */
    public void start() {
        InterceptorManager.getInstance().addInterceptor(this);
    }

    /**
     * Remove this interceptor from the interceptor manager.
     */
    public void stop() {
        InterceptorManager.getInstance().removeInterceptor(this);
    }

    /**
     * Adds all server defined bookmarks to the users requested
     * bookmarks.
     *
     * @param jid            the jid of the user requesting the bookmark(s)
     * @param storageElement the JEP-0048 compliant storage element.
     */
    private void addBookmarks(JID jid, Element storageElement) {
        final Collection<Bookmark> bookmarks = BookmarkManager.getBookmarks();
        for (Bookmark bookmark : bookmarks) {
            // Check to see if the bookmark should be appended for this
            // particular user.
            boolean addBookmarkForUser = bookmark.isGlobalBookmark() || isBookmarkForJID(jid, bookmark);
            if (addBookmarkForUser) {
                // Add bookmark element.
                addBookmarkElement(jid, bookmark, storageElement);
            }
        }
    }

    /**
     * True if the specified bookmark should be appended to the users list of
     * bookmarks.
     *
     * @param jid      the jid of the user.
     * @param bookmark the bookmark.
     * @return true if bookmark should be appended.
     */
    private static boolean isBookmarkForJID(JID jid, Bookmark bookmark) {
        String username = jid.getNode();

        if (bookmark.getUsers().contains(username)) {
            return true;
        }

        Collection<String> groups = bookmark.getGroups();

        if (groups != null && !groups.isEmpty()) {
            GroupManager groupManager = GroupManager.getInstance();
            for (String groupName : groups) {
                try {
                    Group group = groupManager.getGroup(groupName);
                    if (group.isUser(jid.getNode())) {
                        return true;
                    }
                }
                catch (GroupNotFoundException e) {
                    Log.debug(e.getMessage(), e);
                }
            }
        }
        return false;
    }

    /**
     * Adds a Bookmark to the users defined list of bookmarks.
     *
     * @param jid      the users jid.
     * @param bookmark the bookmark to be added.
     * @param element  the storage element to append to.
     */
    private void addBookmarkElement(JID jid, Bookmark bookmark, Element element) {
        final UserManager userManager = UserManager.getInstance();

        try {
            userManager.getUser(jid.getNode());
        }
        catch (UserNotFoundException e) {
            return;
        }

        // If this is a URL Bookmark, check to make sure we
        // do not add duplicate bookmarks.
        if (bookmark.getType() == Bookmark.Type.url) {
            Element urlBookmarkElement = urlExists(element, bookmark.getValue());

            if (urlBookmarkElement == null) {
                urlBookmarkElement = element.addElement("url");
                urlBookmarkElement.addAttribute("name", bookmark.getName());
                urlBookmarkElement.addAttribute("url", bookmark.getValue());
                // Add an RSS attribute to the bookmark if it's defined. RSS isn't an
                // official part of the Bookmark JEP, but we define it as a logical
                // extension.
                boolean rss = Boolean.valueOf(bookmark.getProperty("rss"));
                if (rss) {
                    urlBookmarkElement.addAttribute("rss", Boolean.toString(rss));
                }
            }
            appendSharedElement(urlBookmarkElement);
        }
        // Otherwise it's a conference bookmark.
        else {

            try {
                Element conferenceElement = conferenceExists(element, bookmark.getValue());

                // If the conference bookmark does not exist, add it to the current
                // reply.
                if (conferenceElement == null) {
                    conferenceElement = element.addElement("conference");
                    conferenceElement.addAttribute("name", bookmark.getName());
                    boolean autojoin = Boolean.valueOf(bookmark.getProperty("autojoin"));
                    conferenceElement.addAttribute("autojoin", Boolean.toString(autojoin));
                    conferenceElement.addAttribute("jid", bookmark.getValue());
                }
                appendSharedElement(conferenceElement);

            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Adds the shared namespace element to indicate to clients that this bookmark is a shared bookmark.
     *
     * @param bookmarkElement the bookmark to add the shared element to.
     */
    private static void appendSharedElement(Element bookmarkElement) {
        bookmarkElement.addElement("shared_bookmark", "http://jivesoftware.com/jeps/bookmarks");
    }

    /**
     * Checks if the bookmark has already been defined in the users private storage.
     *
     * @param element the private storage element.
     * @param url     the url to search for.
     * @return true if the bookmark already exists.
     */
    private static Element urlExists(Element element, String url) {
        // Iterate through current elements to see if this url already exists.
        // If one does not exist, then add the bookmark.
        final Iterator<Element> urlBookmarks = element.elementIterator("url");
        while (urlBookmarks.hasNext()) {
            Element urlElement = urlBookmarks.next();
            String urlValue = urlElement.attributeValue("url");
            if (urlValue.equalsIgnoreCase(url)) {
                return urlElement;
            }
        }

        return null;
    }

    /**
     * Checks if the conference bookmark has already been defined in the users private storage.
     *
     * @param element  the private storage element.
     * @param roomJID the JID of the room to find.
     * @return true if the bookmark exists.
     */
    private Element conferenceExists(Element element, String roomJID) {
        // Iterate through current elements to see if the conference bookmark
        // already exists.
        final Iterator<Element> conferences = element.elementIterator("conference");
        while (conferences.hasNext()) {
            final Element conferenceElement = conferences.next();
            String jidValue = conferenceElement.attributeValue("jid");

            if (jidValue != null && roomJID != null && jidValue.equalsIgnoreCase(roomJID)) {
                return conferenceElement;
            }
        }
        return null;
    }
}
