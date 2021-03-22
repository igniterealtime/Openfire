package org.jivesoftware.openfire.vcard;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.jivesoftware.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.*;

/**
 * The JDBC user provider allows you to use an external database to define the vcards.
 * All data is treated as read-only so any set operations will result in an exception.
 * <p>To enable this provider, set the following in the system properties:</p>
 * <ul>
 * <li>{@code provider.vcard.className = org.jivesoftware.openfire.vcard.JDBCVCardProvider}</li>
 * </ul>
 * <p>and an xml jdbc.vcard-mapping.</p>
 * <p>
 * Then you need to set your driver, connection string and SQL statements:
 * </p>
 * <ul>
 * <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver}</li>
 * <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 * <li>{@code jdbcUserProvider.loadVCardSQL = SELECT name, email FROM myUser WHERE user = ?}</li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li>{@code jdbcUserProvider.useConnectionProvider = true}</li>
 * </ul>
 *
 * <p>
 * The vcard attributes can be configured by adding an <code>attrs="attr1,attr2"</code>
 * attribute to the vcard elements.</p>
 * <p>
 * Arbitrary text can be used for the element values as well as <code>MessageFormat</code>
 * style placeholders for the jdbc attributes. For example, if you wanted to map the JDBC
 * attribute <code>displayName</code> to the vcard element <code>FN</code>, the xml
 * nippet would be:</p><br><pre><FN attrs="displayName">{0}</FN></pre>
 * <p>
 * The vCard XML must be escaped in CDATA and must also be well formed. It is the exact
 * XML this provider will send to a client after after stripping <code>attr</code> attributes
 * and populating the placeholders with the data retrieved from JDBC. This system should
 * be flexible enough to handle any client's vCard format. An example mapping follows.<br>
 * </p>
 * {@code jdbc.vcard-mapping =
 *        <![CDATA[
 *    		<vCard xmlns='vcard-temp'>
 *    			<FN attrs="displayName">{0}</FN>
 *    			<NICKNAME attrs="uid">{0}</NICKNAME>
 *    			<BDAY attrs="dob">{0}</BDAY>
 *    			<ADR>
 *    				<HOME/>
 *    				<EXTADR>Ste 500</EXTADR>
 *    				<STREET>317 SW Alder St</STREET>
 *    				<LOCALITY>Portland</LOCALITY>
 *    				<REGION>Oregon</REGION>
 *    				<PCODE>97204</PCODE>
 *    				<CTRY>USA</CTRY>
 *    			</ADR>
 *    			<TEL>
 *    				<HOME/>
 *    				<VOICE/>
 *    				<NUMBER attrs="telephoneNumber">{0}</NUMBER>
 *    			</TEL>
 *    			<EMAIL>
 *    				<INTERNET/>
 *    				<USERID attrs="mail">{0}</USERID>
 *    			</EMAIL>
 *    			<TITLE attrs="title">{0}</TITLE>
 *    			<ROLE attrs="">{0}</ROLE>
 *    			<ORG>
 *    				<ORGNAME attrs="o">{0}</ORGNAME>
 *    				<ORGUNIT attrs="">{0}</ORGUNIT>
 *    			</ORG>
 *    			<URL attrs="labeledURI">{0}</URL>
 *    			<DESC attrs="uidNumber,homeDirectory,loginShell">
 *    				uid: {0} home: {1} shell: {2}
 *    			</DESC>
 *    		</vCard>
 *        ]]>
 * }
 * <p>
 * An easy way to get the vcard format your client needs, assuming you've been
 * using the database store, is to do a <code>SELECT value FROM ofVCard WHERE
 * username='some_user'</code> in your favorite sql querier and paste the result
 * into the <code>vcard-mapping</code> (don't forget the CDATA).</p>
 *
 * @author hamzaozturk
 */
public class JDBCVCardProvider implements VCardProvider, PropertyEventListener {

    public static final SystemProperty<Boolean> STORE_AVATAR_IN_DB = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("jdbc.override.avatar")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(JDBCVCardProvider::setDbStorageEnabled)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(JDBCVCardProvider.class);

    private String connectionString;

    private String loadVCardSQL;
    private boolean useConnectionProvider;

    private VCardTemplate template;
    private static boolean dbStorageEnabled = false;

    /**
     * The default vCard provider is used to handle the vCard in the database. vCard
     * fields that can be overriden are stored in the database.
     *
     * This is used/created only if we are storing avatars in the database.
     */
    private DefaultVCardProvider defaultProvider = null;

    /**
     * Constructs a new JDBC vcard provider.
     */
    public JDBCVCardProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcVCardProvider.loadVCardSQL");
        JiveGlobals.migrateProperty("jdbc.vcard-mapping");

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcVCardProvider.useConnectionProvider");

        // Load the JDBC driver and connection string.
        if (!useConnectionProvider) {
            String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
            try {
                Class.forName(jdbcDriver).newInstance();
            }
            catch (Exception e) {
                Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
                return;
            }
            connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
        }

        // Load database statements for vcard data.
        loadVCardSQL = JiveGlobals.getProperty("jdbcVCardProvider.loadVCardSQL");

        initTemplate();
        // Listen to property events so that the template is always up to date
        PropertyEventDispatcher.addListener(this);
        // DB vcard provider used for loading properties overwritten in the DB
        defaultProvider = new DefaultVCardProvider();
        // Check of avatars can be overwritten (and stored in the database)
        setDbStorageEnabled(STORE_AVATAR_IN_DB.getValue());
    }

    private static void setDbStorageEnabled(final boolean value) {
        dbStorageEnabled = value;
    }

    /**
     * Initializes the VCard template as set by the administrator.
     */
    private void initTemplate() {
        String property = JiveGlobals.getProperty("jdbc.vcard-mapping");
        Log.debug("JDBCVCardProvider: Found vcard mapping: '" + property);
        try {
            // Remove CDATA wrapping element
            if (property.startsWith("<![CDATA[")) {
                property = property.substring(9, property.length()-3);
            }
            Document document = DocumentHelper.parseText(property);
            template = new VCardTemplate(document);
        }
        catch (Exception e) {
            Log.error("Error loading vcard mapping: " + e.getMessage());
        }

        Log.debug("JDBCVCardProvider: attributes size==" + template.getAttributes().length);
    }

    /**
     * XMPP disallows some characters in identifiers, requiring them to be escaped.
     *
     * This implementation assumes that the database returns properly escaped identifiers,
     * but can apply escaping by setting the value of the 'jdbcVCardProvider.isEscaped'
     * property to 'false'.
     *
     * @return 'false' if this implementation needs to escape database content before processing.
     */
    protected boolean assumePersistedDataIsEscaped()
    {
        return JiveGlobals.getBooleanProperty( "jdbcVCardProvider.isEscaped", true );
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider) {
            return DbConnectionManager.getConnection();
        } else {
            return DriverManager.getConnection(connectionString);
        }
    }

    /**
     * Creates a mapping of requested JDBC attributes to their values for the given user.
     *
     * @param username User we are looking up in JDBC.
     * @return Map of JDBC attribute to setting.
     */
    private Map<String, String> getJDBCAttributes(String username) {
        // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
        final String queryValue = assumePersistedDataIsEscaped() ? username : JID.unescapeNode( username );

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Map<String, String> map = new HashMap<>();
        try {
            con = getConnection();
            pstmt = con.prepareStatement(loadVCardSQL);
            pstmt.setString(1, queryValue);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }

            for (String attribute : template.getAttributes()) {
                Object ob = rs.getObject(attribute);
                String value;
                if (ob == null) {
                    Log.debug("JDBCVCardProvider: No jdbc value found for attribute '" + attribute + "'");
                    value = "";
                } else {
                    Log.debug("JDBCVCardProvider: Found attribute "+attribute+" of type: "+ob.getClass());
                    if(ob instanceof String) {
                        value = (String)ob;
                    } else {
                        value = Base64.encodeBytes((byte[])ob);
                    }
                }
                Log.debug("JDBCVCardProvider: Jdbc attribute '" + attribute + "'=>'" + value + "'");
                map.put(attribute, value);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return map;
    }

    /**
     * Loads the avatar from JDBC, based off the vcard template.
     *
     * If enabled, will replace a blank PHOTO element with one from a DB stored vcard.
     *
     * @param username User we are loading the vcard for.
     * @return The loaded vcard element, or null if none found.
     */
    @Override
    public Element loadVCard(String username) {
        Map<String, String> map = getJDBCAttributes(username);
        Log.debug("JDBCVCardProvider: Getting mapped vcard for " + username);
        Element vcard = new VCard(template).getVCard(map);
        // If we have a vcard from jdbc, but it doesn't have an avatar filled in, then we
        // may fill it with a locally stored vcard element.
        if (dbStorageEnabled && vcard != null && (vcard.element("PHOTO") == null || vcard.element("PHOTO").element("BINVAL") == null || vcard.element("PHOTO").element("BINVAL").getText().matches("\\s*"))) {
            Element avatarElement = loadAvatarFromDatabase(username);
            if (avatarElement != null) {
                Log.debug("JDBCVCardProvider: Adding avatar element from local storage");
                Element currentElement = vcard.element("PHOTO");
                if (currentElement != null) {
                    vcard.remove(currentElement);
                }
                vcard.add(avatarElement);
            }
        }

        if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_LOAD, PhotoResizer.PROPERTY_RESIZE_ON_LOAD_DEFAULT ) )
        {
            PhotoResizer.resizeAvatar( vcard );
        }

        Log.debug("JDBCVCardProvider: Returning vcard");
        return vcard;
    }

    /**
     * Loads the avatar element from the user's DB stored vcard.
     *
     * @param username User whose vcard/avatar element we are loading.
     * @return Loaded avatar element or null if not found.
     */
    private Element loadAvatarFromDatabase(String username) {
        Element vcardElement = defaultProvider.loadVCard(username);
        Element avatarElement = null;
        if (vcardElement != null && vcardElement.element("PHOTO") != null) {
            avatarElement = vcardElement.element("PHOTO").createCopy();
        }
        return avatarElement;
    }

    /**
     * Handles when a user creates a new vcard.
     *
     * @param username User that created a new vcard.
     * @param vCardElement vCard element containing the new vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    @Override
    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    /**
     * Handles when a user updates their vcard.
     *
     * @param username User that updated their vcard.
     * @param vCardElement vCard element containing the new vcard.
     * @throws UnsupportedOperationException If an invalid field is changed or we are in readonly mode.
     */
    @Override
    public Element updateVCard(String username, Element vCardElement) throws UnsupportedOperationException {
        if (dbStorageEnabled && defaultProvider != null) {
            if (isValidVCardChange(username, vCardElement)) {
                Element mergedVCard = getMergedVCard(username, vCardElement);
                try {
                    defaultProvider.updateVCard(username, mergedVCard);
                } catch (NotFoundException e) {
                    try {
                        defaultProvider.createVCard(username, mergedVCard);
                    } catch (AlreadyExistsException e1) {
                        // Ignore
                    }
                }
                return mergedVCard;
            }
            else {
                throw new UnsupportedOperationException("JDBCVCardProvider: Invalid vcard changes.");
            }
        }
        else {
            throw new UnsupportedOperationException("JDBCVCardProvider: VCard changes not allowed.");
        }
    }

    /**
     * Delets a user vcard. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend vcard store.
     *
     * @param username the username to delete.
     * @throws UnsupportedOperationException if the provider does not support the
     *                                       operation.
     */
    @Override
    public void deleteVCard(String username) {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true or false if the change to the existing vcard is valid (only to PHOTO element)
     *
     * @param username User who's JDBC-based vcard we will compare with.
     * @param newvCard New vCard Element we will compare against.
     * @return True or false if the changes made were valid (only to PHOTO element)
     */
    private Boolean isValidVCardChange(String username, Element newvCard) {
        if (newvCard == null) {
            // Well if there's nothing to change, of course it's valid.
            Log.debug("JDBCVCardProvider: No new vcard provided (no changes), accepting.");
            return true;
        }
        Map<String, String> map = getJDBCAttributes(username);
        // Retrieve JDBC created vcard for comparison
        Element jdbcvCard = new VCard(template).getVCard(map);
        if (jdbcvCard == null) {
            // This person has no vcard at all, may not change it!
            Log.debug("JDBCVCardProvider: User has no JDBC vcard, nothing they can change, rejecting.");
            return false;
        }
        // If the JDBC vcard has a non-empty PHOTO element set, then there is literally no way this will be accepted.
        Element jdbcPhotoElem = jdbcvCard.element("PHOTO");
        if (jdbcPhotoElem != null) {
            Element jdbcBinvalElem = jdbcPhotoElem.element("BINVAL");
            if (jdbcBinvalElem != null && !jdbcBinvalElem.getTextTrim().matches("\\s*")) {
                // JDBC is providing a valid PHOTO element, byebye!
                Log.debug("JDBCVCardProvider: JDBC has a PHOTO element set, no way to override, rejecting.");
                return false;
            }
        }
        // Retrieve database vcard, if it exists
        Element dbvCard = defaultProvider.loadVCard(username);
        if (dbvCard != null) {
            Element dbPhotoElem = dbvCard.element("PHOTO");
            if (dbPhotoElem == null) {
                // DB has no photo, lets accept what we got.
                Log.debug("JDBCVCardProvider: Database has no PHOTO element, accepting update.");
                return true;
            }
            else {
                Element newPhotoElem = newvCard.element("PHOTO");
                if (newPhotoElem == null) {
                    Log.debug("JDBCVCardProvider: Photo element was removed, accepting update.");
                    return true;
                }
                // Note: NodeComparator never seems to consider these equal, even if they are?
                if (!dbPhotoElem.asXML().equals(newPhotoElem.asXML())) {
                    // Photo element was changed.  Ignore all other changes and accept this.
                    Log.debug("JDBCVCardProvider: PHOTO element changed, accepting update.");
                    return true;
                }
            }
        }
        else {
            // No vcard exists in database
            Log.debug("JDBCVCardProvider: Database has no vCard stored, accepting update.");
            return true;
        }
        // Ok, either something bad changed or nothing changed.  Either way, user either:
        // 1. should not have tried to change something 'readonly'
        // 2. shouldn't have bothered submitting no changes
        // So we'll consider this a bad return.
        Log.debug("JDBCVCardProvider: PHOTO element didn't change, no reason to accept this, rejecting.");
        return false;
    }

    /**
     * Returns a merged JDBC vCard combined with a PHOTO element provided in specified vCard.
     *
     * @param username User whose vCard this is.
     * @param mergeVCard vCard element that we are merging PHOTO element from into the JDBC vCard.
     * @return vCard element after merging in PHOTO element to JDBC data.
     */
    private Element getMergedVCard(String username, Element mergeVCard) {
        Map<String, String> map = getJDBCAttributes(username);
        Log.debug("JDBCVCardProvider: Retrieving JDBC mapped vcard for " + username);
        if (map.isEmpty()) {
            return null;
        }
        Element vcard = new VCard(template).getVCard(map);
        if (mergeVCard == null) {
            // No vcard passed in?  Hrm.  Fine, return JDBC vcard.
            return vcard;
        }
        if (mergeVCard.element("PHOTO") == null) {
            // Merged vcard has no photo element, return JDBC vcard as is.
            return vcard;
        }
        Element photoElement = mergeVCard.element("PHOTO").createCopy();
        if (photoElement == null || photoElement.element("BINVAL") == null || photoElement.element("BINVAL").getText().matches("\\s*")) {
            // We were passed something null or empty, so lets just return the JDBC based vcard.
            return vcard;
        }
        // Now we need to check that the JDBC vcard doesn't have a PHOTO element that's filled in.
        if (!((vcard.element("PHOTO") == null || vcard.element("PHOTO").element("BINVAL") == null || vcard.element("PHOTO").element("BINVAL").getText().matches("\\s*")))) {
            // Hrm, it does, return the original vcard;
            return vcard;
        }
        Log.debug("JDBCVCardProvider: Merging avatar element from passed vcard");
        Element currentElement = vcard.element("PHOTO");
        if (currentElement != null) {
            vcard.remove(currentElement);
        }
        vcard.add(photoElement);
        return vcard;
    }

    /**
     * Returns true if this VCardProvider is read-only. When read-only,
     * vcards can not be created, deleted, or modified.
     *
     * @return true if the vcard provider is read-only.
     */
    @Override
    public boolean isReadOnly() {
        return !dbStorageEnabled;
    }

    /**
     * A property was set. The parameter map {@code params} will contain the
     * the value of the property under the key {@code value}.
     *
     * @param property the name of the property.
     * @param params   event parameters.
     */
    @Override
    public void propertySet(String property, Map<String, Object> params) {
        if ("jdbc.vcard-mapping".equals(property)) {
            initTemplate();
            // Reset cache of vCards
            VCardManager.getInstance().reset();
        }
    }

    /**
     * A property was deleted.
     *
     * @param property the name of the property deleted.
     * @param params   event parameters.
     */
    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        //Ignore
    }

    /**
     * An XML property was set. The parameter map {@code params} will contain the
     * the value of the property under the key {@code value}.
     *
     * @param property the name of the property.
     * @param params   event parameters.
     */
    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
        //Ignore
    }

    /**
     * An XML property was deleted.
     *
     * @param property the name of the property.
     * @param params   event parameters.
     */
    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        //Ignore
    }
}
