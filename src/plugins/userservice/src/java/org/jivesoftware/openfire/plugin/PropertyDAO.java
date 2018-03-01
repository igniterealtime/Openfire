package org.jivesoftware.openfire.plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.exceptions.ExceptionType;
import org.jivesoftware.openfire.exceptions.ServiceException;

/**
 * The Class PropertyDAO.
 */
public class PropertyDAO {

    /** The Constant LOAD_PROPERTY. */
    private final static String LOAD_PROPERTY = "SELECT username FROM ofUserProp WHERE name=? AND propValue=?";

    /** The Constant LOAD_PROPERTY_BY_KEY. */
    private final static String LOAD_PROPERTY_BY_KEY = "SELECT username FROM ofUserProp WHERE name=?";

    /**
     * Gets the username by property key and or value.
     *
     * @param propertyName
     *            the property name
     * @param propertyValue
     *            the property value (can be null)
     * @return the username by property
     * @throws ServiceException
     *             the service exception
     */
    public static List<String> getUsernameByProperty(String propertyName, String propertyValue) throws ServiceException {
        List<String> usernames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            // Load property by key and value
            if (propertyValue != null) {
                pstmt = con.prepareStatement(LOAD_PROPERTY);
                pstmt.setString(1, propertyName);
                pstmt.setString(2, propertyValue);
            } else {
                // Load property by key
                pstmt = con.prepareStatement(LOAD_PROPERTY_BY_KEY);
                pstmt.setString(1, propertyName);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                usernames.add(rs.getString(1));
            }
        } catch (SQLException sqle) {
            throw new ServiceException("Could not get username by property", propertyName,
                    ExceptionType.PROPERTY_NOT_FOUND, Response.Status.NOT_FOUND, sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return usernames;
    }
}
