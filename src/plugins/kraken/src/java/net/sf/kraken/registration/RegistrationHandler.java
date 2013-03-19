/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.registration;

import java.util.Collection;
import java.util.List;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.NameSpace;

import net.sf.kraken.type.PresenceType;
import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.ChannelHandler;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.PacketError.Condition;

/**
 * Handles IQ-register stanzas (as defined by XEP-077) that are used to register
 * or deregister a particular XMPP entity from/to a gateway.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @author Daniel Henninger
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0077.html">XEP-077:&nbsp;In-Band&nbsp;Registration</a>
 */
public class RegistrationHandler implements ChannelHandler<IQ> {

    static Logger Log = Logger.getLogger(RegistrationHandler.class);

    private final BaseTransport parent;

    /**
     * Creates a new RegistrationHandler that can service the transport provided
     * in the first argument.
     *
     * @param parent The transport that is serviced by the new instance.
     */
    public RegistrationHandler(BaseTransport parent) {
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Argument 'parent' cannot be null.");
        }

        this.parent = parent;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.jivesoftware.openfire.ChannelHandler#process(org.xmpp.packet.Packet)
     */

    public void process(IQ packet) throws UnauthorizedException,
            PacketException {

        // sanitize the input
        if (packet == null) {
            throw new IllegalArgumentException(
                    "Argument 'packet' cannot be null.");
        }

        final String xmlns;
        final Element child = (packet).getChildElement();
        if (child != null) {
            xmlns = child.getNamespaceURI();
        }
        else {
            xmlns = null;
        }

        if (xmlns == null) {
            // No namespace defined.
            Log.debug("Cannot process this stanza, as it has no namespace:"
                    + packet.toXML());
            final IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.bad_request);
            parent.sendPacket(error);
            return;
        }

        // done sanitizing, start processing.
        final Element remove = packet.getChildElement().element("remove");
        if (remove != null) {
            // User wants to unregister. =(
            // this.convinceNotToLeave() ... kidding.
            handleDeregister(packet);
        }
        else {
            // handle the request
            switch (packet.getType()) {
                case get:
                    // client requests registration form
                    getRegistrationForm(packet);
                    break;

                case set:
                    // client is providing (filled out) registration form
                    setRegistrationForm(packet);
                    break;

                default:
                    // ignore result and error stanzas.
                    break;
            }
        }
    }

    /**
     * Processes an IQ-register request that is expressing the wish to
     * deregister from a gateway.
     *
     * @param packet the IQ-register stanza.
     */
    private void handleDeregister(final IQ packet) {
        final IQ result = IQ.createResultIQ(packet);

        if (packet.getChildElement().elements().size() != 1) {
            Log.debug("Cannot process this stanza - exactly one"
                    + " childelement of <remove> expected:" + packet.toXML());
            final IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.bad_request);
            parent.sendPacket(error);
            return;
        }

        final JID from = packet.getFrom();
        final JID to = packet.getTo();

        // Tell the end user the transport went byebye.
        final Presence unavailable = new Presence(Presence.Type.unavailable);
        unavailable.setTo(from);
        unavailable.setFrom(to);
        this.parent.sendPacket(unavailable);

        try {
            deleteRegistration(from);
        }
        catch (UserNotFoundException e) {
            Log.debug("Error cleaning up contact list of: " + from);
            result.setError(Condition.registration_required);
        }
        parent.sendPacket(result);
    }

    /**
     * Handles a IQ-register 'get' request, which is to be interpreted as a
     * request for a registration form template. The template will be prefilled
     * with data, if the requestee has a current registration with the gateway.
     *
     * @param packet the IQ-register 'get' stanza.
     * @throws UnauthorizedException if the user is not allowed to make use of the gateway.
     */
    private void getRegistrationForm(IQ packet) throws UnauthorizedException {
        final JID from = packet.getFrom();
        final IQ result = IQ.createResultIQ(packet);

        // search for existing registrations
        String curUsername = null;
        String curPassword = null;
        String curNickname = null;
        Boolean registered = false;
        final Collection<Registration> registrations = RegistrationManager
                .getInstance().getRegistrations(from, parent.transportType);
        if (registrations.iterator().hasNext()) {
            Registration registration = registrations.iterator().next();
            curUsername = registration.getUsername();
            curPassword = registration.getPassword();
            curNickname = registration.getNickname();
            registered = true;
        }

        // Verify that the user is allowed to make use of the gateway.
        if (!registered && !parent.permissionManager.hasAccess(from)) {
            // User does not have permission to register with transport.
            // We want to allow them to change settings if they are already
            // registered.
            throw new UnauthorizedException(LocaleUtils.getLocalizedString(
                    "gateway.base.registrationdeniedbyacls", "kraken"));
        }

        // generate a template registration form.
        final Element response = DocumentHelper.createElement(QName.get(
                "query", NameSpace.IQ_REGISTER));
        final DataForm form = new DataForm(DataForm.Type.form);
        form.addInstruction(parent.getTerminologyRegistration());

        final FormField usernameField = form.addField();
        usernameField.setLabel(parent.getTerminologyUsername());
        usernameField.setVariable("username");
        usernameField.setType(FormField.Type.text_single);
        if (curUsername != null) {
            usernameField.addValue(curUsername);
        }

        final FormField passwordField = form.addField();
        passwordField.setLabel(parent.getTerminologyPassword());
        passwordField.setVariable("password");
        passwordField.setType(FormField.Type.text_private);
        if (curPassword != null) {
            passwordField.addValue(curPassword);
        }

        final String nicknameTerm = parent.getTerminologyNickname();
        if (nicknameTerm != null) {
            FormField nicknameField = form.addField();
            nicknameField.setLabel(nicknameTerm);
            nicknameField.setVariable("nick");
            nicknameField.setType(FormField.Type.text_single);
            if (curNickname != null) {
                nicknameField.addValue(curNickname);
            }
        }

        response.add(form.getElement());
        response.addElement("instructions").addText(
                parent.getTerminologyRegistration());

        // prefill the template with existing data if a registration already
        // exists.
        if (registered) {
            response.addElement("registered");
            response.addElement("username").addText(curUsername);
            if (curPassword == null) {
                response.addElement("password");
            }
            else {
                response.addElement("password").addText(curPassword);
            }
            if (nicknameTerm != null) {
                if (curNickname == null) {
                    response.addElement("nick");
                }
                else {
                    response.addElement("nick").addText(curNickname);
                }
            }
        }
        else {
            response.addElement("username");
            response.addElement("password");
            if (nicknameTerm != null) {
                response.addElement("nick");
            }
        }

        // Add special indicator for rosterless gateway handling.
        response.addElement("x")
                .addNamespace("", NameSpace.IQ_GATEWAY_REGISTER);

        result.setChildElement(response);

        parent.sendPacket(result);
    }

    /**
     * Handles a IQ-register 'set' request, which is to be interpreted as a
     * request to create a new registration.
     *
     * @param packet the IQ-register 'set' stanza.
     * @throws UnauthorizedException if the user isn't allowed to register.
     */
    private void setRegistrationForm(IQ packet) throws UnauthorizedException {
        final JID from = packet.getFrom();

        final boolean registered;
        Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(from, parent.transportType);
        if (registrations.iterator().hasNext()) {
            registered = true;
        }
        else {
            registered = false;
        }

        if (!registered && !parent.permissionManager.hasAccess(from)) {
            // User does not have permission to register with transport.
            // We want to allow them to change settings if they are already
            // registered.
            throw new UnauthorizedException(LocaleUtils.getLocalizedString(
                    "gateway.base.registrationdeniedbyacls", "kraken"));
        }

        // Parse the input variables
        String username = null;
        String password = null;
        String nickname = null;
        try {
            if (packet.getChildElement().element("x") != null) {
                final DataForm form = new DataForm(packet.getChildElement()
                        .element("x"));
                final List<FormField> fields = form.getFields();
                for (final FormField field : fields) {
                    final String var = field.getVariable();
                    if (var.equals("username")) {
                        username = field.getValues().get(0);
                    }
                    else if (var.equals("password")) {
                        password = field.getValues().get(0);
                    }
                    else if (var.equals("nick")) {
                        nickname = field.getValues().get(0);
                    }
                }
            }
        }
        // TODO: This shouldn't be done by catching an Exception - check for the
        // existence of elements instead. If we insist doing this with an
        // exception handler, prevent catching a generic Exception (catch more
        // specific subclasses instead).
        catch (Exception ex) {
            // No with data form apparently
            Log.info("Most likely, no dataform was present "
                    + "in the IQ-register request.", ex);
        }

        // input variables could also exist in the non-extended elements
        final Element userEl = packet.getChildElement().element("username");
        final Element passEl = packet.getChildElement().element("password");
        final Element nickEl = packet.getChildElement().element("nick");
        if (userEl != null) {
            username = userEl.getTextTrim();
        }
        if (passEl != null) {
            password = passEl.getTextTrim();
        }
        if (nickEl != null) {
            nickname = nickEl.getTextTrim();
        }

        username = (username == null || username.equals("")) ? null : username;
        password = (password == null || password.equals("")) ? null : password;
        nickname = (nickname == null || nickname.equals("")) ? null : nickname;

        // verify that we've got wat we need.
        if (username == null
                || (parent.isPasswordRequired() && password == null)
                || (parent.isNicknameRequired() && nickname == null)) {
            // Invalid information from stanza, lets yell.
            Log.info("Cannot process IQ register request, as it "
                    + "fails to provide all data that's required: "
                    + packet.toXML());
            final IQ result = IQ.createResultIQ(packet);
            result.setError(Condition.bad_request);
            parent.sendPacket(result);
            return;
        }

        // Check if the client supports our proprietary 'rosterless' mode.
        final boolean rosterlessMode;
        final Element x = packet.getChildElement().element("x");
        if (x != null && x.getNamespaceURI() != null
                && x.getNamespaceURI().equals(NameSpace.IQ_GATEWAY_REGISTER)) {
            rosterlessMode = true;
            Log.info("Registering " + packet.getFrom() + " as " + username
                    + " in rosterless mode.");
        }
        else {
            rosterlessMode = false;
            Log.info("Registering " + packet.getFrom() + " as " + username
                    + " (without making use of rosterless mode).");
        }

        // Here's where the true magic lies: create the registration!
        try {
            addNewRegistration(from, username, password, nickname, rosterlessMode);

            registrations = RegistrationManager.getInstance().getRegistrations(from, parent.transportType);
            Registration registration = registrations.iterator().next();
            TransportSession session = parent.registrationLoggedIn(registration, from, PresenceType.available, "", -1);
            session.setRegistrationPacket(packet);
            session.detachSession();
            parent.getSessionManager().storeSession(from, session);

            //final IQ result = IQ.createResultIQ(packet);
            // I believe this shouldn't be included. Leaving it around just in
            // case.
            // Element response =
            // DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
            // result.setChildElement(response);
            //parent.sendPacket(result);
        }
        catch (UserNotFoundException e) {
            Log.warn("Someone attempted to register with the gateway "
                    + "who is not registered with the server: " + from);
            final IQ eresult = IQ.createResultIQ(packet);
            eresult.setError(Condition.forbidden);
            parent.sendPacket(eresult);
            final Message em = new Message();
            em.setType(Message.Type.error);
            em.setTo(packet.getFrom());
            em.setFrom(packet.getTo());
            em.setBody(LocaleUtils.getLocalizedString(
                    "gateway.base.registrationdeniednoacct", "kraken"));
            parent.sendPacket(em);
        }
        catch (IllegalAccessException e) {
            Log.warn("Someone who is not a user of this server "
                    + "tried to register with the transport: " + from);
            final IQ eresult = IQ.createResultIQ(packet);
            eresult.setError(Condition.forbidden);
            parent.sendPacket(eresult);
            final Message em = new Message();
            em.setType(Message.Type.error);
            em.setTo(packet.getFrom());
            em.setFrom(packet.getTo());
            em.setBody(LocaleUtils.getLocalizedString(
                    "gateway.base.registrationdeniedbyhost", "kraken"));
            parent.sendPacket(em);
        }
        catch (IllegalArgumentException e) {
            Log.warn("Someone attempted to register with the "
                    + "gateway with an invalid username: " + from);
            final IQ eresult = IQ.createResultIQ(packet);
            eresult.setError(Condition.bad_request);
            parent.sendPacket(eresult);
            final Message em = new Message();
            em.setType(Message.Type.error);
            em.setTo(packet.getFrom());
            em.setFrom(packet.getTo());
            em.setBody(LocaleUtils.getLocalizedString(
                    "gateway.base.registrationdeniedbadusername", "kraken"));
            parent.sendPacket(em);
        }
    }

    public void completeRegistration(TransportSession session) {
        final IQ result = IQ.createResultIQ(session.getRegistrationPacket());
        if (!session.getFailureStatus().equals(ConnectionFailureReason.NO_ISSUE)) {
            // Ooh there was a connection issue, we're going to report that back!
            if (session.getFailureStatus().equals(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT)) {
                result.setError(Condition.not_authorized);
            }
            else if (session.getFailureStatus().equals(ConnectionFailureReason.CAN_NOT_CONNECT)) {
                result.setError(Condition.service_unavailable);
            }
            else if (session.getFailureStatus().equals(ConnectionFailureReason.LOCKED_OUT)) {
                result.setError(Condition.forbidden);
            }
            else {
                result.setError(Condition.undefined_condition);
            }
            result.setType(IQ.Type.error);
        }
        parent.sendPacket(result);

        session.setRegistrationPacket(null);

        // Lets ask them what their presence is, maybe log them in immediately.
        final Presence p = new Presence(Presence.Type.probe);
        p.setTo(session.getJID());
        p.setFrom(parent.getJID());
        parent.sendPacket(p);
    }

    /**
     * Adds a registration with this transport, or updates an existing one.
     *
     * @param jid          JID of user to add registration to.
     * @param username     Legacy username of registration.
     * @param password     Legacy password of registration.
     * @param nickname     Legacy nickname of registration.
     * @param noRosterItem True if the transport is not to show up in the user's roster.
     * @throws UserNotFoundException    if registration or roster not found.
     * @throws IllegalAccessException   if jid is not from this server.
     * @throws IllegalArgumentException if username is not valid for this transport type.
     */
    public void addNewRegistration(JID jid, String username, String password,
                                   String nickname, Boolean noRosterItem)
            throws UserNotFoundException, IllegalAccessException {
        Log.debug("Adding or updating registration for : " + jid.toString()
                + " / " + username);

        if (!XMPPServer.getInstance().isLocal(jid)) {
            throw new IllegalAccessException(
                    "Domain of jid registering does not match domain of server.");
        }

        if (!parent.isUsernameValid(username)) {
            throw new IllegalArgumentException(
                    "Username specified is not valid for this transport type.");
        }

        final Collection<Registration> registrations = RegistrationManager
                .getInstance().getRegistrations(jid, parent.transportType);
        boolean foundReg = false;
        boolean triggerRestart = false;
        for (final Registration registration : registrations) {
            if (!registration.getUsername().equals(username)) {
                Log.debug("Deleting existing registration before"
                        + " creating a new one: " + registration);
                RegistrationManager.getInstance().deleteRegistration(
                        registration);
            }
            else {
                Log.debug("Existing registration found that can be updated: "
                        + registration);
                if ((registration.getPassword() != null && password == null)
                        || (registration.getPassword() == null && password != null)
                        || (registration.getPassword() != null
                        && password != null && !registration
                        .getPassword().equals(password))) {
                    Log.debug("Updating password for existing registration: "
                            + registration);
                    registration.setPassword(password);
                    triggerRestart = true;
                }
                if ((registration.getNickname() != null && nickname == null)
                        || (registration.getNickname() == null && nickname != null)
                        || (registration.getNickname() != null
                        && nickname != null && !registration
                        .getNickname().equals(nickname))) {
                    Log.debug("Updating nickname for existing registration: "
                            + registration);
                    registration.setNickname(nickname);
                    triggerRestart = true;
                }
                foundReg = true;
            }

            // if a change was made to the registration, restart it.
            if (triggerRestart) {
                try {
                    Log.debug("An existing registration was "
                            + "updated. Restarting the related session: "
                            + registration);
                    final TransportSession relatedSession = parent.sessionManager
                            .getSession(registration.getJID().getNode());
                    parent.registrationLoggedOut(relatedSession);
                }
                catch (NotFoundException e) {
                    // No worries, move on.
                }
            }
        }

        if (!foundReg) {
            RegistrationManager.getInstance().createRegistration(jid,
                    parent.transportType, username, password, nickname);
            triggerRestart = true;
        }

        if (triggerRestart) {
            Log.debug("Clean up any leftover roster items "
                    + "from other transports for: " + jid);
            try {
                parent.cleanUpRoster(jid, !noRosterItem);
            }
            catch (UserNotFoundException ee) {
                throw new UserNotFoundException("Unable to find roster.");
            }
        }

        if (!noRosterItem) {
            try {
                Log.debug("Adding Transport roster item to the roster of: "
                        + jid);
                parent.addOrUpdateRosterItem(jid, parent.getJID(), parent
                        .getDescription(), "Transports");
            }
            catch (UserNotFoundException e) {
                throw new UserNotFoundException(
                        "User not registered with server.");
            }
        }
        else {
            Log.debug("Not adding Transport roster item to the roster of: "
                    + jid + " (as this was explicitly requested).");
        }
    }

    /**
     * Removes a registration from this transport.
     *
     * @param jid JID of user to add registration to.
     * @throws UserNotFoundException if registration or roster not found.
     */
    public void deleteRegistration(JID jid) throws UserNotFoundException {
        Collection<Registration> registrations = RegistrationManager
                .getInstance().getRegistrations(jid, parent.transportType);
        if (registrations.isEmpty()) {
            throw new UserNotFoundException("User was not registered.");
        }

        // Log out any active sessions.
        try {
            TransportSession session = parent.sessionManager.getSession(jid);
            if (session.isLoggedIn()) {
                parent.registrationLoggedOut(session);
            }
            parent.sessionManager.removeSession(jid);
        }
        catch (NotFoundException e) {
            // Ok then.
        }

        // For now, we're going to have to just nuke all of these. Sorry.
        for (final Registration reg : registrations) {
            RegistrationManager.getInstance().deleteRegistration(reg);
        }

        // Clean up the user's contact list.
        try {
            parent.cleanUpRoster(jid, false, true);
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }
}
