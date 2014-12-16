/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import org.jitsi.jicofo.util.*;

import org.osgi.framework.*;

/**
 * Class takes care of creating and removing temporary XMPP account while
 * exposing protocol provider service to end user.
 *
 * @author Pawel Domas
 */
public class ProtocolProviderHandler
    implements RegistrationStateChangeListener
{
    /**
     * XMPP provider factory used to create and destroy XMPP account used by
     * the focus.
     */
    private ProtocolProviderFactory xmppProviderFactory;

    /**
     * XMPP account used by the focus.
     */
    private AccountID xmppAccount;

    /**
     * XMPP protocol provider service used by the focus.
     */
    private ProtocolProviderService protocolService;

    /**
     * Registration listener notified about encapsulated protocol service
     * instance registration state changes.
     */
    private RegistrationStateChangeListener regListener;

    /**
     * Start this instance by created XMPP account using igven parameters.
     * @param serverAddress XMPP server address.
     * @param xmppDomain XMPP authentication domain.
     * @param xmppLoginPassword XMPP login(optional).
     * @param nickName authentication login.
     * @param listener the listener that will be notified about created protocol
     *                 provider's registration state changes.
     */
    public void start(String serverAddress,
                      String xmppDomain,
                      String xmppLoginPassword,
                      String nickName,
                      RegistrationStateChangeListener listener)
    {
        this.regListener = listener;

        xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                    FocusBundleActivator.bundleContext,
                    ProtocolNames.JABBER);

        if (xmppLoginPassword != null)
        {
            xmppAccount
                = xmppProviderFactory.createAccount(
                FocusAccountFactory.createFocusAccountProperties(
                    serverAddress,
                    xmppDomain, nickName, xmppLoginPassword));
        }
        else
        {
            xmppAccount
                = xmppProviderFactory.createAccount(
                FocusAccountFactory.createFocusAccountProperties(
                    serverAddress,
                    xmppDomain, nickName));
        }

        if (!xmppProviderFactory.loadAccount(xmppAccount))
        {
            throw new RuntimeException(
                "Failed to load account: " + xmppAccount);
        }

        ServiceReference protoRef
            = xmppProviderFactory.getProviderForAccount(xmppAccount);

        protocolService
            = (ProtocolProviderService)
                    FocusBundleActivator.bundleContext.getService(protoRef);

        protocolService.addRegistrationStateChangeListener(this);
    }

    /**
     * Stops this instance and removes temporary XMPP account.
     */
    public void stop()
    {
        protocolService.removeRegistrationStateChangeListener(this);

        xmppProviderFactory.uninstallAccount(xmppAccount);
    }

    /**
     * Passes registration state changes of encapsulated protocol provider to
     * registered {@lnik #regListener}.
     *
     * {@inheritDoc}
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        if (regListener != null)
        {
            regListener.registrationStateChanged(evt);
        }
    }

    /**
     * Utility method for obtaining operation sets from underlying protocol
     * provider service.
     */
    public <T extends OperationSet> T getOperationSet(Class<T> opSetClass)
    {
        return protocolService.getOperationSet(opSetClass);
    }

    /**
     * Returns <tt>true</tt> if underlying protocol provider service has
     * registered.
     */
    public boolean isRegistered()
    {
        return protocolService.isRegistered();
    }

    /**
     * Starts registration process of underlying protocol provider service.
     */
    public void register()
    {
        // FIXME: not pooled thread created
        new RegisterThread(protocolService).start();
    }

    /**
     * Returns underlying protocol provider service instance if this
     * <tt>ProtocolProviderHandler</tt> has been started or <tt>null</tt>
     * otherwise.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return protocolService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return protocolService != null
            ? protocolService.toString() : super.toString();
    }
}
