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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages global bookmarks. Bookmarks are defined by
 * <a href="http://www.jabber.org/jeps/jep-0048.html">JEP-0048</a>. Users can define and
 * manage their own bookmarks. Global bookmarks add to a user's own bookmarks and are
 * defined by system administrators to apply to all users, groups, or sets of users.
 *
 * @see Bookmark
 * @author Derek DeMoro
 */
public class BookmarkManager {

	private static final Logger Log = LoggerFactory.getLogger(BookmarkManager.class);

    private static final String DELETE_BOOKMARK = "DELETE FROM ofBookmark where bookmarkID=?";
    private static final String SELECT_BOOKMARKS = "SELECT bookmarkID from ofBookmark";

    /**
     * Returns the specified bookmark.
     *
     * @param bookmarkID the ID of the bookmark.
     * @return the bookmark.
     * @throws NotFoundException if the bookmark could not be found or loaded.
     */
    public Bookmark getBookmark(long bookmarkID) throws NotFoundException {
        // TODO add caching
        return new Bookmark(bookmarkID);
    }

    /**
     * Returns all bookmarks.
     *
     * @return the collection of bookmarks.
     */
    public static Collection<Bookmark> getBookmarks() {
        // TODO: add caching.
        List<Bookmark> bookmarks = new ArrayList<Bookmark>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_BOOKMARKS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                long bookmarkID = rs.getLong(1);
                try {
                    Bookmark bookmark = new Bookmark(bookmarkID);
                    bookmarks.add(bookmark);
                }
                catch (NotFoundException nfe) {
                    Log.error(nfe.getMessage(), nfe);
                }
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return bookmarks;
    }

    /**
     * Deletes a bookmark with the specified bookmark ID.
     *
     * @param bookmarkID the ID of the bookmark to remove from the database.
     */
    public static void deleteBookmark(long bookmarkID) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_BOOKMARK);
            pstmt.setLong(1, bookmarkID);
            pstmt.execute();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }
}
