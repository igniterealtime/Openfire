package org.jivesoftware.wildfire.gateway;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.wildfire.gateway.roster.ForeignContact;
import org.jivesoftware.wildfire.gateway.roster.NormalizedJID;
import org.jivesoftware.wildfire.gateway.roster.PersistenceManager;
import org.jivesoftware.wildfire.gateway.roster.Roster;
import org.jivesoftware.wildfire.gateway.roster.Status;
import org.jivesoftware.wildfire.gateway.util.BackgroundThreadFactory;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.PacketError.Condition;

/**
 * Handle a good share of the tasks for a gateway.  Dealing with lookups for ids,
 * dealing with registration, dealing with presence information.
 * 
 * @author ncampbell
 * @version 1.0
 */
public abstract class BaseGateway implements Gateway, Component, Runnable {

    /**
     * @see org.jivesoftware.wildfire.gateway.Gateway#getName()
     */
    public abstract String getName();

    /**
     * @see org.jivesoftware.wildfire.gateway.Gateway#setName(String)
     */
    public abstract void setName(String newname);
    
    /**
     * @see org.jivesoftware.wildfire.gateway.Gateway#getDescription()
     */
    public abstract String getDescription();
    
    /**
     * @see org.jivesoftware.wildfire.gateway.Gateway#getDomain()
     */
    public final String getDomain() {
        String domainName = this.componentManager.getServerName();
        logger.log(Level.FINE, "basegateway.domainname", domainName);
        return domainName;
    }
    
    /**
     * @return version the version of the gateway
     */
    public abstract String getVersion();
    
    /**
     * @return gatewayType the type of gateway.  THis should always be "gateway"
     */
    public String getType() {
        return "gateway";
    }
    
    /** The logger. @see java.util.logging.Logger */
    private static final Logger logger = Logger.getLogger("BaseGateway","gateway_i18n");

    /**
     * Jabber endpoint.
     */
    protected JabberEndpoint jabberEndpoint;
    
    /**
     * Component Manager to handle communication to the XMPP server.
     */
    public final ComponentManager componentManager = ComponentManagerFactory.getComponentManager();
    
    /** 
     * JID of component.
     */
    public JID jid;
    
    /**
     * Handles registration for this gateway.
     */
    public final PersistenceManager rosterManager = PersistenceManager.Factory.get(this);
    

    
    /** The threadPool. @see java.util.concurrent.ScheduledExecutorService */
    protected static final ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor(new BackgroundThreadFactory());
    
    /**
     * Handle Foreign Contacts and JID Mapping
     */
    private final Map<JID, String> foreignContacts = new HashMap<JID, String>();
    
    /**
     * helper method for getting the ns from a packet.
     * 
     * @param packet
     * @return namespace The namespaceUri.
     */
    private String getNS(IQ packet) {
    	Element childElement = (packet).getChildElement();
        String namespace = null;
        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }
    	
    	return namespace;
    }
    
    /**
     * Process an IQ packet.
     * 
     * @param iq the IQ packet sent from the client.
     * @return packetList A list of Packets to be sent to the client.
     * @see java.util.List
     * @see org.xmpp.packet.IQ
     */
    private List<Packet> processPacket(IQ iq) {
    	String namespace = getNS(iq);
    	List<Packet> response = new ArrayList<Packet>();
    	if("http://jabber.org/protocol/disco#info".equals(namespace)) {
    		response.add(handleServiceDiscovery(iq));
    	} else if("jabber:iq:agents".equals(namespace)) {
    		response.add(handleLegacyServiceDiscovery(iq));
    	} else if("http://jabber.org/protocol/disco#items".equals(namespace)) {
    		response.add(handleDiscoveryItems(iq));
    	} else if("jabber:iq:register".equals(namespace)) {
    		response.addAll(handleRegisterInBand(iq));
    	} else if("jabber:iq:version".equals(namespace)) {
    		response.add(handleVersion(iq));
    	} else if("jabber:iq:browse".equals(namespace)) {
    		response.add(handleBrowse(iq));
    	}
    	return response;
    }
    
    /**
     * Proces a browse request.
     * 
     * @param iq The client IQ packet.
     * @return iq The response IQ packet.
     */
    private IQ handleBrowse(IQ iq) {
    	IQ reply = IQ.createResultIQ(iq);
    	Element responseElement = DocumentHelper.createElement(QName.get(
                "query", "jabber:iq:browse"));
    	
        try {
        
    	for(ForeignContact fc : PersistenceManager.Factory.get(this).getRegistrar().getGatewaySession(iq.getFrom()).getContacts()) {
    		Element item = DocumentHelper.createElement("item");
    		item.addAttribute("category", "user");
    		item.addAttribute("jid", fc + "@" + this.getName() + "." + this.getDomain());
    		item.addAttribute("type", "client");
    		responseElement.add(item);
    		
    	}
        
    	reply.setChildElement(responseElement);
    	
        } catch (Exception e) {
            logger.log(Level.WARNING, "basegateway.notfound", iq.getFrom().toBareJID());
        }
        return reply;
    }
    
    
    /**
     * Process a version request.
     * 
     * @param iq The client IQ packet.
     * @return iq The response to the client.
     */
    private IQ handleVersion(IQ iq) {
    	IQ reply = IQ.createResultIQ(iq);
    	Element responseElement = DocumentHelper.createElement(QName.get(
                "query", "jabber:iq:version"));
    	responseElement.addElement("name").addText(this.getName());
    	responseElement.addElement("version").addText(this.getVersion());
    	responseElement.addElement("os").addText(System.getProperty("os.name"));
    	reply.setChildElement(responseElement);
    	return reply;
    }
    
    /**
     * Handle a register command...this may be the request or the response for 
     * the registration process.
     * 
     * @param iq The client iq packet.
     * @return response The <code>Collection</code> of <code>Packet</code> that 
     * make up the response.
     */
    private Collection<Packet> handleRegisterInBand(final IQ iq) {
    	Element remove = iq.getChildElement().element("remove");
    	Collection<Packet> response = new ArrayList<Packet>();
    	if(remove != null) {
    		response.addAll(unregister(iq));
    	} else {
    		response.addAll(register(iq));
    	}
        
        return response;
    }

    /**
     * Handle a unregister request.
     * 
     * @param iq The IQ packet sent from the client.
     * @return response The packets that make up the response.
     */
    private Collection<Packet> unregister(IQ iq) {
    	IQ reply = IQ.createResultIQ(iq);
        PersistenceManager.Factory.get(this).remove(iq.getFrom());
        
        Collection<Packet> results = new ArrayList<Packet>();
        results.add(reply);
        
        Presence unsubscribed, unsubscribe, unavailable;
        
        unavailable = new Presence(Presence.Type.unavailable);
        unavailable.setTo(reply.getTo());
        unavailable.setFrom(reply.getFrom());
        
        unsubscribed = new Presence(Presence.Type.unsubscribed);
        unsubscribed.setTo(reply.getTo());
        unsubscribed.setFrom(reply.getFrom());
        
        unsubscribe = new Presence(Presence.Type.unsubscribe);
        unsubscribe.setTo(reply.getTo());
        unsubscribe.setFrom(reply.getFrom());
                
        results.add(unsubscribe);
        results.add(unsubscribed);
        results.add(unavailable);
        
        logger.log(Level.INFO, "basegateway.unregister", iq.getFrom());
        
    	return results;
    }
    
    /**
     * Handle a register request.
     * 
     * @param iq The client's IQ packet.
     * @return response A <code>Collection</code> of <code>Packet</code>s that make up the response.
     */
    private Collection<Packet> register(IQ iq) {	
    	
        Collection<Packet> response = new ArrayList<Packet>();
    	
        IQ reply = IQ.createResultIQ(iq);
        Element responseElement = DocumentHelper.createElement(QName.get(
                "query", "jabber:iq:register"));
    	if(iq.getType().equals(IQ.Type.set)) {
            
            String username = null;
            String password = null;
            
            try {
                DataForm form = new DataForm(iq.getChildElement().element("x"));
                List<FormField> fields = form.getFields();
                
                for(FormField field : fields) {
                    String var = field.getVariable();
                    if(var.equalsIgnoreCase("username")) {
                        username = field.getValues().get(0); 
                    } else if (var.equalsIgnoreCase("password")) {
                        /** 
                         * The password is stored in Whack and DOM4J as a String 
                         * so the password is sent in the clear and stored in  
                         * JVM until termination. 
                         */
                        password = field.getValues().get(0);
                    }
                }
            } catch (Exception e) {
                // unable to use dataform
                logger.log(Level.FINER, "basegateway.dataformnotused", e);
            }

            if(username == null || password == null) {
                
                // try non DataForm.
                Element usernameElement = iq.getChildElement().element("username");
                Element passwordElement = iq.getChildElement().element("password");
                
                if(usernameElement != null) {
                    username = usernameElement.getTextTrim();
                }
                
                if(passwordElement != null) {
                    password = passwordElement.getTextTrim();
                }
            }
                
            // make sure that something was collected, otherwise return an error.
            if(username == null || password == null) {
                    IQ result = IQ.createResultIQ(iq);
                    result.setError(Condition.bad_request);
                    response.add(result);
            } else {
            
                logger.log(Level.INFO, "basegateway.register", username.trim());
                SubscriptionInfo info = new SubscriptionInfo(username.trim(), password);
        		PersistenceManager.Factory.get(this).getRegistrar().add(iq.getFrom(), info);
        		reply.setChildElement(responseElement);
        		response.add( reply );
            
                Presence subscribe = new Presence(Presence.Type.subscribe);
                subscribe.setTo(iq.getFrom());
                subscribe.setFrom(iq.getTo());
                response.add(subscribe);
            }
            
    	} else if(iq.getType().equals(IQ.Type.get)) { 
            
            DataForm form = new DataForm(DataForm.Type.form);
            
            form.addInstruction("Please enter your AIM Screenname and Password");
            FormField usernameField = form.addField();
            usernameField.setLabel("Username");
            usernameField.setVariable("username");
            usernameField.setType(FormField.Type.text_single);
    		FormField passwordField = form.addField();
            passwordField.setLabel("Password");
            passwordField.setVariable("password");
            passwordField.setType(FormField.Type.text_private);
            
            /**
             * Add standard elements to request.
             */
            responseElement.addElement("instruction").addText("Please enter your AIM screenname and password");
            responseElement.addElement("username");
            responseElement.addElement("password");
            
            /**
             * Add data form (for those that can support it.
             */
            responseElement.add(form.getElement());
            
            reply.setChildElement(responseElement);
    		response.add( reply );
    	}
        
        return response;
    }
    
    /**
     * Returns the contacts for the user that made the request.
     * 
     * @param iq The client request.
     * @return response The IQ packet response.
     */
    private IQ handleDiscoveryItems(IQ iq) {
    	IQ reply = IQ.createResultIQ(iq);
    	Element responseElement = DocumentHelper.createElement(QName.get(
                "query", "http://jabber.org/protocol/disco#info"));
    
        Roster roster = this.rosterManager.getContactManager().getRoster(iq.getFrom());
        for( ForeignContact entry : roster.getAll()) {
            Element item = responseElement.addElement("item");
            item.addAttribute("jid", entry.getJid().toBareJID());
            item.addAttribute("name", entry.getName());
            responseElement.add(item);
        }
        reply.setChildElement(responseElement);
        System.out.println(reply);
        return reply;
    }
    
    /**
     * TODO: handle a legacy service discovery request.
     * 
     * @param iq
     * @return response
     */
    private IQ handleLegacyServiceDiscovery(IQ iq) {
    	return IQ.createResultIQ(iq);
    	// TODO: Implement Legacy Service Discovery.
    }
    /**
     * Handle service discovery.
     * 
     * @param iq The client IQ packet.
     * @return respones The IQ response.
     */
    private IQ handleServiceDiscovery(IQ iq) {
    	IQ reply = IQ.createResultIQ(iq);
    	
        Element responseElement = DocumentHelper.createElement(QName.get(
                "query", "http://jabber.org/protocol/disco#info"));
        responseElement.addElement("identity").addAttribute("category", "gateway")
                                              .addAttribute("type", this.getType())
                                              .addAttribute("name", this.getDescription());
        responseElement.addElement("feature").addAttribute("var", "jabber:iq:time");
    	responseElement.addElement("feature").addAttribute("var", "jabber:iq:version");
    	responseElement.addElement("feature").addAttribute("var", "jabber:iq:register");
        reply.setChildElement(responseElement);
    	return reply;
    }
    
    /**
     * Dispatch the appropriate message type.
     * 
     * @param packet The packet to process.
     */
    public void processPacket(Packet packet) {
    	try {
    		List<Packet> response  = new ArrayList<Packet>();
    		if(packet instanceof IQ) {
    			response.addAll( processPacket((IQ)packet) );
    		} else if (packet instanceof Presence) {
    			response.addAll( processPacket((Presence)packet) );
    		} else if (packet instanceof Message) {
    			processPacket((Message)packet);
    		} else {
    			System.out.println("UNHANDLED: " + packet.toString());
    		}
    		
    		// process
    		if(response.size() > 0) {
    			for(Packet p : response) {
    				componentManager.sendPacket(this, p);
    			}
    		} else {
    			//System.out.println("Request[" + packet.toString() + "] with no response");
    		}
    
    	} catch (Exception e) {
    		e.printStackTrace();
    		throw new RuntimeException(e);
    	}
    }
    /**
     * Process a message from the client.
     * 
     * @param message Client <code>Message</code>
     * @throws Exception
     * @see org.xmpp.packet.Message
     */
    private void processPacket(Message message) throws Exception {
        logger.finer(message.toString());
        
    	GatewaySession session = PersistenceManager.Factory.get(this).getRegistrar().getGatewaySession(message.getFrom());
    	session.getLegacyEndpoint().sendPacket(message);
    }

    /**
     * Proces a presense packet.
     * 
     * @param presence The presence packet from the client.
     * @return list A <code>List</code> of <code>Presence</code> packets.
     * @throws Exception 
     */
    private List<Presence> processPacket(Presence presence) throws Exception {
    	List<Presence> p = new ArrayList<Presence>();
    	JID from = presence.getFrom();
    	        
        logger.finest(presence.toString());
    	
        /*
         * Unknown entity is trying to access the gateway.
         */
    	if(!rosterManager.getRegistrar().isRegistered(NormalizedJID.wrap(from))) {
            logger.log(Level.INFO, "basegateway.unabletofind", from);
            // silently ignore a delete request
            if(!Presence.Type.unavailable.equals(presence.getType())) {
                logger.log(Level.INFO, "basegateway.unauthorizedrequest", new Object[] { presence.getType(), from.toString() });
                Presence result = new Presence();
                result.setError(Condition.not_authorized);
                result.setStatus(LocaleUtils.getLocalizedString("basegateway.registerfirst", "gateway"));
                p.add(result);
            }
    		return p;
    	}
        
        /*
         * Get the underlying session for this JID and handle accordingly. 
         */
    	GatewaySession sessionInfo = rosterManager.getRegistrar().getGatewaySession(from);
        if(sessionInfo == null) {
            logger.log(Level.WARNING, "basegateway.unabletolocatesession" , from);
            return p;
        }

        if(Presence.Type.subscribe.equals(presence.getType())) {
            if(presence.getTo().equals(this.jid)) {
                sessionInfo.getSubscriptionInfo().serverRegistered = true;
            } else {
                Presence subscribe = new Presence(Presence.Type.subscribe);
                subscribe.setTo(presence.getFrom());
                subscribe.setFrom(presence.getTo());
                p.add(subscribe);
            }
            Presence reply = new Presence(Presence.Type.subscribed);
            reply.setTo(presence.getFrom());
            reply.setFrom(presence.getTo());
            p.add(reply);
            
    	}
        else if (Presence.Type.subscribed.equals(presence.getType())) {
            if(presence.getTo().equals(this.jid)) { // subscribed to server
                sessionInfo.getSubscriptionInfo().clientRegistered = true;    
            } else { // subscribe to legacy user
                logger.log(Level.FINE,"basegateway.subscribed");
            }
    		
    	}
        else if (Presence.Type.unavailable.equals(presence.getType())){
            /**
             * If an unavailable presence stanza is received then logout the 
             * current user and send back and unavailable stanza.
             */
    		if(sessionInfo.isConnected()) {
    			sessionInfo.logout();
    		}
           
            Presence reply = new Presence(Presence.Type.unavailable);
            reply.setTo(presence.getFrom());
            reply.setFrom(presence.getTo());
            p.add(reply);
    	}
        else if (presence.getTo().getNode() == null) { // this is a request to the gateway only.
            Presence result = new Presence();
            result.setTo(presence.getFrom());
            result.setFrom(this.jid);
            p.add(result);
            logger.log(Level.FINE, "basegateway.gatewaypresence");
        }
        else {
            GatewaySession session = rosterManager.getRegistrar().getGatewaySession(presence.getFrom());
            
            try {
                ForeignContact fc = session.getContact(presence.getTo());
                Status status = fc.getStatus();
                Presence p2 = new Presence();
                p2.setFrom(presence.getTo());
                p2.setTo(presence.getFrom());
                if(status.isOnline()) {
                    p2.setStatus(status.getValue());
                }
                else {
                    p2.setType(Presence.Type.unavailable);
                }
                p.add(p2);
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "basegateway.statusexception",
                        new Object[]{presence.getTo(), presence.getFrom(), e.getLocalizedMessage()});
            }
    	}
    	return p;
    }

    /**
     * Return the JID of this component.
     * 
     * @return jid The JID for this gateway.
     * @see Gateway#getJID()
     */
    public JID getJID() {
        return this.jid;
    }
    
    /**
     * Handle initialization.
     * 
     * @param jid The JID for this component
     * @param componentManager The <code>ComponentManager</code> associated with this component
     * @throws ComponentException 
     * 
     * @see org.xmpp.component.Component#initialize(JID, ComponentManager)
     */
    public void initialize(JID jid, ComponentManager componentManager) throws ComponentException {
    	this.jid = jid;
        EndpointValve jabberEndpointValve = new EndpointValve(false);
        this.jabberEndpoint = new JabberEndpoint(componentManager, this, jabberEndpointValve);
    }
    
    /**
     * Reverse lookup.  This will always succeed.  A new JID will be created for a 
     * foreign contact.
     * 
     * @param foreignContact The foreign contact name.
     * @return jid The JID associated with this contact.
     */
    public JID whois(String foreignContact) {
    	JID jid = new JID(foreignContact, this.getName() + "." + this.getDomain(), null);
    	foreignContacts.put(jid, foreignContact);
    	return jid;
    }
    
    /**
     * Lookup a foreign contact
     * 
     * @param jid The JID to lookup.
     * @return contact A String for the foreign contact or null if non is regestered.
     */
    public String whois(JID jid) {
    	return foreignContacts.get(jid);
    }

    /**
     * Start this component
     */
    public void start() {
        threadPool.scheduleAtFixedRate(this, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Shutdown this component
     */
    public void shutdown() {
        threadPool.shutdown();
    }

    /** 
     * Do maintenance, essentially send out a presense ping.  Will also attempt to 
     * subscribed the contact to the JID.
     */
    public void run() {
        
        logger.log(Level.FINEST, "basegateway.maintenancestart");
        
        for(SubscriptionInfo si : rosterManager.getRegistrar().getAllGatewaySessions()) {
            if(!si.clientRegistered) {
                Presence p = new Presence();
                p.setType(Presence.Type.subscribe);
                p.setTo(si.jid);
                p.setFrom(this.jid);
                try {
                    componentManager.sendPacket(this, p);
                } catch (ComponentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
//            for(ForeignContact fc : rosterManager.getContactManager().getRoster(si.jid).getAll()) {
//                Presence p = new Presence();
//                p.setFrom(fc.getJid());
//                p.setTo(si.jid);
//                p.setStatus(fc.status.getValue());
//                try {
//                    componentManager.sendPacket(this, p);
//                } catch (Exception e) {
//                    logger.log(Level.WARNING, "basegateway.unabletosendpresence", e);
//                }
//            }
            
        }
        
//        for(ForeignContact contact : rosterManager.getContactManager().getAllForeignContacts()) {
//            if(!contact.isConnected()) 
//                continue;
//            Presence p = new Presence();
//            p.setFrom(contact.getJid());
//            try {
//                componentManager.sendPacket(this, p);
//            } catch (Exception e) {
//                logger.log(Level.WARNING, "basegateway.unabletosendpresence", e);
//            }  
//        }
        
        logger.log(Level.FINEST, "basegateway.maintenancestop");
    }
    
    /**
     * Returns a <code>SessionFactory</code>.  The session factory utilizes the
     * abstract method <code>getSessionInstance</code> to get a new instance and
     * set the jabber endpoint associated with this <code>SessionFactory</code>
     * 
     * @see org.jivesoftware.wildfire.gateway.Gateway#getSessionFactory()
     * @see #getSessionInstance(SubscriptionInfo)
     */
    public SessionFactory getSessionFactory() {
        return new SessionFactory() {

            public GatewaySession newInstance(SubscriptionInfo info) {
                GatewaySession session = getSessionInstance(info);
                session.setJabberEndpoint(jabberEndpoint);
                return session;
            }
            
        };
    }
    
    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#getValve()
     */
    public EndpointValve getValve() {
        return this.jabberEndpoint.getValve();
    }
    
    /**
     * Return a <code>GatewaySession</code> given the <code>SubscriptionInfo</code>
     * 
     * @param info The subscription information to use to create the gateway.
     * @return session A new gateway session.
     * @see #getSessionFactory()
     * @see Gateway#getSessionFactory()
     */
    protected abstract GatewaySession getSessionInstance(SubscriptionInfo info);
}
