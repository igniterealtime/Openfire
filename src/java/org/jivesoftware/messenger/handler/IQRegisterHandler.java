/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.IQImpl;
import org.jivesoftware.messenger.spi.PresenceImpl;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * Implements the TYPE_IQ jabber:iq:register protocol (plain only). Clients
 * use this protocol to register a user account with the server.
 * A 'get' query runs a register probe to obtain the fields needed
 * for registration. Return the registration form.
 * A 'set' query attempts to create a new user account
 * with information given in the registration form.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 * <p/>
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

    private static MetaDataFragment probeResult;
    private IQHandlerInfo info;
    // TODO: this value needs to be shared across all instances but not across the entire jvm...
    private static boolean enabled;
    private Map delegates = new HashMap();

    /**
     * <p>Basic constructor does nothing.</p>
     */
    public IQRegisterHandler() {
        super("XMPP Registration Handler");
        info = new IQHandlerInfo("query", "jabber:iq:register", IQImpl.class);
    }

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        if (probeResult == null) {
            // Create the basic element of the probeResult which contains the basic registration
            // information (e.g. username, passoword and email)
            Element element = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));
            element.addElement("username");
            element.addElement("password");
            element.addElement("email");

            // Create the registration form to include in the probeResult. The form will include
            // the basic information plus name and visibility of name and email.
            // TODO Future versions could allow plugin modules to add new fields to the form 
            XDataFormImpl registrationForm = new XDataFormImpl(DataForm.TYPE_FORM);
            registrationForm.setTitle("XMPP Client Registration");
            registrationForm.addInstruction("Please provide the following information");

            XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
            field.setType(FormField.TYPE_HIDDEN);
            field.addValue("jabber:iq:register");
            registrationForm.addField(field);

            field = new XFormFieldImpl("username");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Username");
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("name");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Full name");
            registrationForm.addField(field);

            field = new XFormFieldImpl("email");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Email");
            registrationForm.addField(field);

            field = new XFormFieldImpl("password");
            field.setType(FormField.TYPE_TEXT_PRIVATE);
            field.setLabel("Password");
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("x-nameVisible");
            field.setType(FormField.TYPE_BOOLEAN);
            field.setLabel("Show name");
            field.addValue("1");
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("x-emailVisible");
            field.setType(FormField.TYPE_BOOLEAN);
            field.setLabel("Show email");
            field.addValue("0");
            field.setRequired(true);
            registrationForm.addField(field);

            // Create the probeResult and add the basic info together with the registration form
            probeResult = new MetaDataFragment(element);
            probeResult.addFragment(registrationForm);
        }
        // Check for the default case where no inband property is set and
        // make the default true (allowing inband registration)
        String inband = JiveGlobals.getProperty("register.inband");
        if (inband == null || "".equals(inband)) {
            setInbandRegEnabled(true);
        }
        else {
            enabled = "true".equals(inband);
        }
    }

    public synchronized IQ handleIQ(IQ packet) throws
            PacketException, UnauthorizedException, XMLStreamException {
        // Look for a delegate for this packet
        IQHandler delegate = getDelegate(packet.getRecipient());
        // We assume that the registration packet was meant to the server if delegate is
        // null
        if (delegate != null) {
            // Pass the packet to the real packet handler
            return delegate.handleIQ(packet); 
        }
        
        Session session = packet.getOriginatingSession();
        IQ reply = null;
        if (!enabled) {
            reply = packet.createResult();
            reply.setError(XMPPError.Code.FORBIDDEN);
        }
        else if (IQ.GET.equals(packet.getType())) {
            reply = packet.createResult();
            if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                try {
                    User user = userManager.getUser(session.getUserID());
                    MetaDataFragment currentRegistration = (MetaDataFragment) probeResult
                            .createDeepCopy();
                    currentRegistration.setProperty("query.registered", null);
                    currentRegistration.setProperty("query.username", user.getUsername());
                    currentRegistration.setProperty("query.password", null);
                    currentRegistration.setProperty("query.email", user.getInfo().getEmail());
                    
                    XDataFormImpl form = (XDataFormImpl) currentRegistration.getFragment(
                        "x",
                        "jabber:x:data");
                    form.getField("username").addValue(user.getUsername());
                    form.getField("name").addValue(user.getInfo().getName());
                    form.getField("email").addValue(user.getInfo().getEmail());
                    // Clear default value and set new value
                    FormField field = form.getField("x-nameVisible");
                    field.clearValues();
                    field.addValue((user.getInfo().isNameVisible() ? "1" : "0"));
                    // Clear default value and set new value
                    field = form.getField("x-emailVisible");
                    field.clearValues();
                    field.addValue((user.getInfo().isEmailVisible() ? "1" : "0"));
                    
                    reply.setChildFragment(currentRegistration);
                }
                catch (UserNotFoundException e) {
                    reply.setChildFragment(probeResult);
                }
                catch (UnauthorizedException e) {
                    reply.setChildFragment(probeResult);
                }
            }
            else {
                reply.setChildFragment(probeResult);
            }
        }
        else if (IQ.SET.equals(packet.getType())) {
            try {
                XMPPFragment iq = packet.getChildFragment();
                MetaDataFragment metaData = MetaDataFragment.convertToMetaData(iq);
                if (metaData.includesProperty("query.remove")) {
                    if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                        // Send an unavailable presence to the user's subscribers
                        // Note: This gives us a chance to send an unavailable presence to the 
                        // entities that the user sent directed presences
                        Presence presence = new PresenceImpl();
                        presence.setAvailable(false);
                        presence.setVisible(false);
                        presence.setSender(packet.getSender());
                        presenceHandler.process(presence);
                        // Delete the user
                        userManager.deleteUser(session.getUserID());
                        reply = packet.createResult();
                        session.getConnection().deliver(reply);
                        // Close the user's connection
                        session.getConnection().close();
                    }
                    else {
                        throw new UnauthorizedException();
                    }
                }
                else {
                    String username = null;
                    String password = null;
                    String email = null;
                    User newUser = null;
                    XDataFormImpl registrationForm = null;
                    FormField field;

                    // We prefer to assume that iq is an XMPPDOMFragment for performance reasons. 
                    // The other choice is to use metaData.convertToDOMFragment() which creates new 
                    // objects.
                    Element formElement = ((XMPPDOMFragment)iq).getRootElement().element("x");
                    // Check if a form was used to provide the registration info
                    if (formElement != null) {
                        // Get the sent form
                        registrationForm = new XDataFormImpl();
                        registrationForm.parse(formElement);
                        // Get the username sent in the form
                        Iterator values = registrationForm.getField("username").getValues();
                        username = (values.hasNext() ? (String)values.next() : " ");
                        // Get the password sent in the form
                        field = registrationForm.getField("password");
                        if (field != null) {
                            values = field.getValues();
                            password = (values.hasNext() ? (String)values.next() : " ");
                        }
                        // Get the email sent in the form
                        field = registrationForm.getField("email");
                        if (field != null) {
                            values = field.getValues();
                            email = (values.hasNext() ? (String)values.next() : " ");
                        }
                    }
                    else {
                        // Get the registration info from the query elements
                        username = metaData.getProperty("query.username");
                        password = metaData.getProperty("query.password");
                        email = metaData.getProperty("query.email");
                    }
                    if (email == null || "".equals(email)) {
                        email = " ";
                    }

                    // Inform the entity of failed registration if some required information was
                    // not provided
                    if (password == null || password.trim().length() == 0) {
                        reply = packet.createResult();
                        reply.setError(XMPPError.Code.NOT_ACCEPTABLE);
                        return reply;
                    }

                    if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                        User user = userManager.getUser(session.getUserID());
                        if (user != null) {
                            if (user.getUsername().equalsIgnoreCase(username)) {
                                user.setPassword(password);
                                user.getInfo().setEmail(email);
                                newUser = user;
                            }
                            else {
                                // An admin can create new accounts when logged in.
                                if (user.isAuthorized(Permissions.SYSTEM_ADMIN)) {
                                    newUser = userManager.createUser(username, password, email);
                                }
                                else {
                                    throw new UnauthorizedException();
                                }
                            }
                        }
                        else {
                            throw new UnauthorizedException();
                        }
                    }
                    else {
                        newUser = userManager.createUser(username, password, email);
                    }
                    // Set and save the extra user info (e.g. Full name, name visible, etc.)
                    if (newUser != null && registrationForm != null) {
                        Iterator values;
                        // Get the full name sent in the form
                        field = registrationForm.getField("name");
                        if (field != null) {
                            values = field.getValues();
                            String name = (values.hasNext() ? (String)values.next() : " ");
                            newUser.getInfo().setName(name);
                        }
                        // Get the name visible flag sent in the form
                        values = registrationForm.getField("x-nameVisible").getValues();
                        String visible = (values.hasNext() ? (String)values.next() : "1");
                        boolean nameVisible = ("1".equals(visible) ? true : false);
                        // Get the email visible flag sent in the form
                        values = registrationForm.getField("x-emailVisible").getValues();
                        visible = (values.hasNext() ? (String)values.next() : "0");
                        boolean emailVisible = ("1".equals(visible) ? true : false);
                        // Save the extra user info
                        newUser.getInfo().setNameVisible(nameVisible);
                        newUser.getInfo().setEmailVisible(emailVisible);
                        newUser.saveInfo();
                    }

                    reply = packet.createResult();
                }
            }
            catch (UserAlreadyExistsException e) {
                reply = packet.createResult();
                reply.setError(XMPPError.Code.CONFLICT);
            }
            catch (UserNotFoundException e) {
                reply = packet.createResult();
                reply.setError(XMPPError.Code.BAD_REQUEST);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (reply != null) {
            // why is this done here instead of letting the iq handler do it?
            session.getConnection().deliver(reply);
        }
        return null;
    }

    public boolean isInbandRegEnabled() {
        return enabled;
    }

    public void setInbandRegEnabled(boolean allowed) {
        enabled = allowed;
        JiveGlobals.setProperty("register.inband", enabled ? "true" : "false");
    }
    
    private IQHandler getDelegate(XMPPAddress recipientJID) {
        if (recipientJID == null) {
            return null;
        }
        return (IQHandler) delegates.get(recipientJID.getHostPrep());
    }
    
    public void addDelegate(String serviceName, IQHandler delegate) {
        // TODO As long as we only add delegates during server startup there is no need to make
        // the iv "delegates" thread-safe. In the future, when we implement the component JEP we 
        // must remove all this idea of the delegates since IQRouter will directly send the IQ 
        // packet to the correct IQHandler. In that time, this class will only be an
        // IQHandler that creates or removes user accounts and not a generic IQRegisterHandler.  
        delegates.put(serviceName, delegate);
    }

    public void removeDelegate(String serviceName) {
        delegates.remove(serviceName);
    }

    public UserManager userManager;
    public PresenceUpdateHandler presenceHandler;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(PresenceUpdateHandler.class, "presenceHandler");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:register");
        return features.iterator();
    }
}
