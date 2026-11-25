/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.handler;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Implements the TYPE_IQ jabber:iq:register protocol (plain only). Clients
 * use this protocol to register a user account with the server.
 * A 'get' query runs a register probe to obtain the fields needed
 * for registration. Return the registration form.
 * A 'set' query attempts to create a new user account
 * with information given in the registration form.
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <h2>Compatibility</h2>
 * The current behavior is designed to emulate jabberd1.4. However
 * this behavior differs significantly from JEP-0078 (non-SASL registration).
 * In particular, authentication (IQ-Auth) must return an error when a user
 * request is made to an account that doesn't exist to trigger auto-registration
 * (JEP-0078 explicitly recommends against this practice to prevent hackers
 * from probing for legitimate accounts).
 *
 * @author Iain Shigeoka
 */
public class IQRegisterHandler extends IQHandler implements ServerFeaturesProvider {

    private static final Logger Log = LoggerFactory.getLogger(IQRegisterHandler.class);

    private static final String NAMESPACE = "jabber:iq:register";

    /**
     * Controls in-band account creation and deletion.
     */
    public static final SystemProperty<Boolean> INBAND_REGISTRATION = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("register.inband")
        .setDefaultValue(true)
        .setDynamic(true)
        .addListener(enabled -> updateAdvertisedFeature())
        .build();

    /**
     * Controls users updating (and reading) their own registration: their password, name and email address.
     */
    public static final SystemProperty<Boolean> PASSWORD_CHANGE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("register.password")
        .setDefaultValue(true)
        .setDynamic(true)
        .addListener(enabled -> updateAdvertisedFeature())
        .build();
    private static Element probeResult;

    private UserManager userManager;
    private RosterManager rosterManager;

    private IQHandlerInfo info;

    /**
     * <p>Basic constructor does nothing.</p>
     */
    public IQRegisterHandler() {
        super("XMPP Registration Handler");
        info = new IQHandlerInfo("query", NAMESPACE);
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();

        if (probeResult == null) {
            // Create the basic element of the probeResult which contains the basic registration
            // information (e.g. username, passoword and email)
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));
            probeResult.addElement("username");
            probeResult.addElement("password");
            probeResult.addElement("email");
            probeResult.addElement("name");

            // Create the registration form to include in the probeResult. The form will include
            // the basic information plus name and visibility of name and email.
            // TODO Future versions could allow plugin modules to add new fields to the form 
            final DataForm registrationForm = new DataForm(DataForm.Type.form);
            registrationForm.setTitle("XMPP Client Registration");
            registrationForm.addInstruction("Please provide the following information");

            final FormField fieldForm = registrationForm.addField();
            fieldForm.setVariable("FORM_TYPE");
            fieldForm.setType(FormField.Type.hidden);
            fieldForm.addValue("jabber:iq:register");

            final FormField fieldUser = registrationForm.addField();
            fieldUser.setVariable("username");
            fieldUser.setType(FormField.Type.text_single);
            fieldUser.setLabel("Username");
            fieldUser.setRequired(true);

            final FormField fieldName = registrationForm.addField(); 
            fieldName.setVariable("name");
            fieldName.setType(FormField.Type.text_single);
            fieldName.setLabel("Full name");
            if (UserManager.getUserProvider().isNameRequired()) {
                fieldName.setRequired(true);
            }

            final FormField fieldMail = registrationForm.addField();
            fieldMail.setVariable("email");
            fieldMail.setType(FormField.Type.text_single);
            fieldMail.setLabel("Email");
            if (UserManager.getUserProvider().isEmailRequired()) {
                fieldMail.setRequired(true);
            }

            final FormField fieldPwd = registrationForm.addField();
            fieldPwd.setVariable("password");
            fieldPwd.setType(FormField.Type.text_private);
            fieldPwd.setLabel("Password");
            fieldPwd.setRequired(true);

            // Add the registration form to the probe result.
            probeResult.add(registrationForm.getElement());
        }
        
        JiveGlobals.migrateProperty("register.inband");
        JiveGlobals.migrateProperty("register.password");
        
    }

    @Override
    public IQ handleIQ(IQ packet) throws PacketException, UnauthorizedException {
        final ClientSession session = sessionManager.getSession(packet.getFrom());
        // If no session was found then answer an error (if possible)
        if (session == null) {
            Log.warn("Error during registration. No session found for '{}'", packet.getFrom());
            // This error packet probably won't make it through
            return createErrorReply(packet, PacketError.Condition.internal_server_error);
        }

        final IQ reply;
        if (IQ.Type.get.equals(packet.getType())) {
            reply = session.isAuthenticated() ? handleReadRegistration(session, packet) : handleRegistrationForm(packet);
        }
        else if (IQ.Type.set.equals(packet.getType())) {
            reply = handleSet(session, packet);
        }
        else {
            reply = null;
        }

        if (reply != null) {
            // The reply is delivered directly to the session, instead of being returned for the IQ handler to route.
            // A returned reply goes through regular routing, which cannot deliver to a session that has not
            // authenticated yet. That is the case for an entity that is registering an account, which would then
            // never receive a response (OF-3382).
            session.process(reply);
        }
        return null;
    }

    /**
     * Routes a request that modifies a registration to the operation that it is for, and translates the failures of
     * that operation to an error reply. Before that, the request is checked: this is done before the operation checks
     * if it is allowed, which is the order that the handler has always used (XEP-0077 does not say which error takes
     * precedence when a request is refused for more than one reason).
     *
     * @return the reply to deliver, or null if the reply was already delivered.
     */
    private IQ handleSet(final ClientSession session, final IQ packet) {
        try {
            final Element query = packet.getChildElement();
            if (query.element("remove") != null) {
                // XEP-0077 3.2: <remove/> must be the only child element of the query.
                if (query.elements().size() != 1) {
                    return createErrorReply(packet, PacketError.Condition.bad_request);
                }
                return handleRemoveAccount(session, packet);
            }

            final String username = preparedUsername(query);
            return session.isAuthenticated() ? handleUpdateRegistration(session, packet, username) : handleCreateAccount(packet, username);
        }
        catch (UserAlreadyExistsException e) {
            return createErrorReply(packet, PacketError.Condition.conflict);
        }
        catch (UserNotFoundException e) {
            return createErrorReply(packet, PacketError.Condition.bad_request);
        }
        catch (StringprepException e) {
            // The specified username is not correct according to the stringprep specs
            return createErrorReply(packet, PacketError.Condition.jid_malformed);
        }
        catch (IllegalArgumentException e) {
            // At least one of the fields passed in is not valid
            Log.warn(e.getMessage(), e);
            return createErrorReply(packet, PacketError.Condition.not_acceptable);
        }
        catch (UnsupportedOperationException e) {
            // The User provider is read-only so this operation is not allowed
            return createErrorReply(packet, PacketError.Condition.not_allowed);
        }
        catch (Exception e) {
            // Some unexpected error happened so return an internal_server_error
            Log.error(e.getMessage(), e);
            return createErrorReply(packet, PacketError.Condition.internal_server_error);
        }
    }

    // What is allowed. These are the rules that OF-3388 describes: account creation and deletion are governed by
    // in-band registration, and a user updating their own registration by 'register.password'.

    private static boolean mayCreateAccount() {
        return INBAND_REGISTRATION.getValue();
    }

    private static boolean mayDeleteAccount() {
        return INBAND_REGISTRATION.getValue();
    }

    private static boolean mayUpdateRegistration() {
        return PASSWORD_CHANGE.getValue();
    }

    /**
     * Reading an existing registration is possible when either setting is enabled, in line with when the feature is
     * advertised.
     */
    private static boolean mayReadRegistration() {
        return mayCreateAccount() || mayUpdateRegistration();
    }

    // What is done. Each operation checks if it is allowed first, and returns the reply that is to be delivered.

    /**
     * Returns the fields that an entity needs to provide to register an account. Retrieving the registration form is
     * part of creating an account.
     */
    private IQ handleRegistrationForm(final IQ packet) {
        if (!mayCreateAccount()) {
            // XEP-0077 3.1: a host that does not support in-band registration MUST return service-unavailable.
            return createErrorReply(packet, PacketError.Condition.service_unavailable);
        }
        final IQ reply = IQ.createResultIQ(packet);
        // This is a workaround. Since we don't want to have an incorrect TO attribute
        // value we need to clean up the TO attribute. The TO attribute will contain an
        // incorrect value since we are setting a fake JID until the user actually
        // authenticates with the server.
        reply.setTo((JID) null);
        reply.setChildElement(probeResult.createCopy());
        return reply;
    }

    /**
     * Returns the registration of the authenticated user.
     */
    private IQ handleReadRegistration(final ClientSession session, final IQ packet) {
        if (!mayReadRegistration()) {
            return createErrorReply(packet, PacketError.Condition.service_unavailable);
        }
        final IQ reply = IQ.createResultIQ(packet);
        try {
            User user = userManager.getUser(session.getUsername());
            Element currentRegistration = probeResult.createCopy();
            currentRegistration.addElement("registered");
            currentRegistration.element("username").setText(user.getUsername());
            currentRegistration.element("password").setText("");
            currentRegistration.element("email")
                    .setText(user.getEmail() == null ? "" : user.getEmail());
            currentRegistration.element("name").setText(user.getName());

            Element form = currentRegistration.element(QName.get("x", "jabber:x:data"));
            Iterator<Element> fields = form.elementIterator("field");
            Element field;
            while (fields.hasNext()) {
                field = fields.next();
                if ("username".equals(field.attributeValue("var"))) {
                    field.addElement("value").addText(user.getUsername());
                }
                else if ("name".equals(field.attributeValue("var"))) {
                    field.addElement("value").addText(user.getName());
                }
                else if ("email".equals(field.attributeValue("var"))) {
                    field.addElement("value")
                            .addText(user.getEmail() == null ? "" : user.getEmail());
                }
            }
            reply.setChildElement(currentRegistration);
        }
        catch (UserNotFoundException e) {
            reply.setChildElement(probeResult.createCopy());
        }
        return reply;
    }

    /**
     * Creates the account that an entity that is not authenticated registers.
     *
     * @param username the prepared username that was provided, or null if there was none.
     */
    private IQ handleCreateAccount(final IQ packet, final String username) throws Exception {
        if (!mayCreateAccount()) {
            return createErrorReply(packet, PacketError.Condition.service_unavailable);
        }

        final Element query = packet.getChildElement();
        final String password = submittedValue(query, "password").orElse(null);
        final String email = blankToNull(submittedValue(query, "email").orElse(null));
        final String name = blankToNull(submittedValue(query, "name").orElse(null));

        // Inform the entity of failed registration if some required information was not provided
        if (username == null || password == null || password.trim().isEmpty()) {
            return createErrorReply(packet, PacketError.Condition.not_acceptable);
        }

        final User newUser = userManager.createUser(username, password, name, email);
        // Set and save the extra user info (e.g. full name, etc.)
        if (newUser != null && name != null && !name.equals(newUser.getName())) {
            newUser.setName(name);
        }
        return IQ.createResultIQ(packet);
    }

    /**
     * Updates the registration of the authenticated user: their password, name and email address. An authenticated
     * entity can only modify its own registration (XEP-0077). Creating accounts on behalf of others is not supported:
     * use XEP-0133 add-user, the admin console or the REST API plugin.
     *
     * @param username the prepared username that was provided, or null if there was none.
     */
    private IQ handleUpdateRegistration(final ClientSession session, final IQ packet, final String username) throws Exception {
        final Element query = packet.getChildElement();

        // Which account is targeted is checked before the settings are: another account is never allowed.
        final User user = userManager.getUser(session.getUsername());
        if (username == null) {
            // XEP-0077 3.3: the request does not contain complete information.
            return createErrorReply(packet, PacketError.Condition.bad_request);
        }
        if (!user.getUsername().equalsIgnoreCase(username)) {
            Log.debug("Rejecting registration request from '{}' for another user '{}'.", session.getAddress(), username);
            return createErrorReply(packet, PacketError.Condition.forbidden);
        }
        if (!mayUpdateRegistration()) {
            // XEP-0077 3.3
            return createErrorReply(packet, PacketError.Condition.not_allowed);
        }

        final String password = submittedValue(query, "password").orElse(null);
        final Optional<String> email = submittedValue(query, "email");
        final Optional<String> name = submittedValue(query, "name");

        // Reject explicitly emptied required fields before changing anything.
        if ((email.isPresent() && blankToNull(email.get()) == null && UserManager.getUserProvider().isEmailRequired())
            || (name.isPresent() && blankToNull(name.get()) == null && UserManager.getUserProvider().isNameRequired())) {
            return createErrorReply(packet, PacketError.Condition.not_acceptable);
        }

        if (password != null && !password.trim().isEmpty()) {
            user.setPassword(password);
        }
        // Omitted fields are left unchanged, explicitly empty ones are cleared.
        if (email.isPresent()) {
            user.setEmail(blankToNull(email.get()));
        }
        if (name.isPresent()) {
            user.setName(blankToNull(name.get()));
        }
        return IQ.createResultIQ(packet);
    }

    /**
     * Removes the account of the authenticated user.
     *
     * Unlike the other operations, the reply is delivered before the connections of the account are closed, so that
     * the client receives it. Therefore, this returns null once the account is removed.
     */
    private IQ handleRemoveAccount(final ClientSession session, final IQ packet) throws Exception {
        if (!mayDeleteAccount()) {
            // XEP-0077 3.2
            return createErrorReply(packet, PacketError.Condition.not_allowed);
        }
        if (!session.isAuthenticated()) {
            // The entity is not registered, so there is nothing to remove.
            return createErrorReply(packet, PacketError.Condition.registration_required);
        }

        final User user = userManager.getUser(session.getUsername());
        // Delete the user
        userManager.deleteUser(user);
        // Delete the roster of the user
        rosterManager.deleteRoster(session.getAddress());
        // Delete the user from all the Groups
        GroupManager.getInstance().deleteUser(user);

        session.process(IQ.createResultIQ(packet));
        // Take a quick nap so that the client can process the result
        Thread.sleep(10);
        // Close the user's connection
        final StreamError error = new StreamError(StreamError.Condition.not_authorized);
        for (ClientSession sess : sessionManager.getSessions(XMPPServer.getInstance().createJID(user.getUsername(), null))) {
            sess.close(error);
        }
        return null;
    }

    /**
     * Returns a value that was submitted in a registration request, either as a field in a data form, or as an element.
     *
     * @param query the registration query of the request.
     * @param field the name of the field.
     * @return empty if the request does not have the field, or an empty string if the field has no value. This
     * distinguishes a field that was omitted (which leaves a value as it is) from one that was left empty (which clears
     * it).
     */
    private static Optional<String> submittedValue(final Element query, final String field) {
        final Element formElement = query.element("x");
        if (formElement != null) {
            final FormField formField = new DataForm(formElement).getField(field);
            if (formField == null) {
                return Optional.empty();
            }
            final List<String> values = formField.getValues();
            return Optional.of(values.isEmpty() ? "" : values.get(0));
        }
        if (query.element(field) == null) {
            return Optional.empty();
        }
        return Optional.of(query.elementText(field));
    }

    /**
     * Returns the username that a request provides, prepared. The prepared form is what identifies the account. A
     * missing or blank username is treated as not provided.
     *
     * @return the prepared username, or null if the request does not provide one.
     * @throws StringprepException if the username is not valid. This is checked up front, so that a more informative
     * error can be sent.
     */
    private static String preparedUsername(final Element query) throws StringprepException {
        final String username = blankToNull(submittedValue(query, "username").orElse(null));
        return username == null ? null : Stringprep.nodeprep(username);
    }

    private static String blankToNull(final String value) {
        return value == null || value.matches("\\s*") ? null : value;
    }

    /**
     * Creates an error reply to the provided request. The request's payload is included, unless it contains a
     * password: that should not be echoed back (XEP-0077 3.3).
     */
    private static IQ createErrorReply(final IQ request, final PacketError.Condition condition) {
        final IQ reply = IQ.createResultIQ(request);
        if (!containsPassword(request.getChildElement())) {
            reply.setChildElement(request.getChildElement().createCopy());
        }
        reply.setError(condition);
        return reply;
    }

    /**
     * Checks if a registration query contains a password, either as an element or as a field in a data form.
     */
    private static boolean containsPassword(final Element query) {
        if (query.element("password") != null) {
            return true;
        }
        final Element form = query.element(QName.get("x", "jabber:x:data"));
        if (form != null) {
            for (final Element field : form.elements("field")) {
                if ("password".equals(field.attributeValue("var"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInbandRegEnabled()
    {
        return INBAND_REGISTRATION.getValue() && !UserManager.getUserProvider().isReadOnly();
    }

    public void setInbandRegEnabled(boolean allowed)
    {
        if ( allowed && UserManager.getUserProvider().isReadOnly() )
        {
            Log.warn( "Enabling in-band registration has no effect, as the user provider for this system is read-only." );
        }
        INBAND_REGISTRATION.setValue(allowed);
    }

    public boolean canChangePassword()
    {
        return PASSWORD_CHANGE.getValue() && !UserManager.getUserProvider().isReadOnly();
    }

    public void setCanChangePassword(boolean allowed)
    {
        if ( allowed && UserManager.getUserProvider().isReadOnly() )
        {
            Log.warn( "Allowing password changes has no effect, as the user provider for this system is read-only." );
        }
        PASSWORD_CHANGE.setValue(allowed);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    /**
     * The feature is only advertised when at least one registration operation can succeed: creating (and deleting)
     * accounts, or updating an existing registration.
     */
    @Override
    public Iterator<String> getFeatures() {
        if (isRegistrationAvailable()) {
            return Collections.singleton(NAMESPACE).iterator();
        }
        return Collections.emptyIterator();
    }

    private static boolean isRegistrationAvailable() {
        return !UserManager.getUserProvider().isReadOnly() && mayReadRegistration();
    }

    /**
     * Brings the feature that is advertised in service discovery in line with the current settings.
     */
    private static void updateAdvertisedFeature() {
        final XMPPServer server = XMPPServer.getInstance();
        final IQDiscoInfoHandler discoInfoHandler = server == null ? null : server.getIQDiscoInfoHandler();
        if (discoInfoHandler == null) {
            return; // Not (yet) running as part of a server.
        }
        if (isRegistrationAvailable()) {
            discoInfoHandler.addServerFeature(NAMESPACE);
        } else {
            discoInfoHandler.removeServerFeature(NAMESPACE);
        }
    }
}
