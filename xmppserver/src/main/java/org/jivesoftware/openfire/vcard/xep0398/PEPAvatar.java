package org.jivesoftware.openfire.vcard.xep0398;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.PhotoResizer;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

public class PEPAvatar
{
    private static final Logger Log = LoggerFactory.getLogger(PEPAvatar.class);

    //ID = SHA1-HASH of field image
    private String id		= null;
    private byte[] image	= null;
    private int height		= -1;
    private int width		= -1;
    private String mimetype = null;

    private PEPService pep  = null;

    //Namespaces
    public static String NAMESPACE_METADATA = "urn:xmpp:avatar:metadata";
    public static String NAMESPACE_DATA = "urn:xmpp:avatar:data";
    public static String NAMESPACE_VCARDUPDATE = "vcard-temp:x:update";
    public static String NAMESPACE_PUBSUB = "http://jabber.org/protocol/pubsub";

    //Propertystring for JiveGlobals
    public static String PROPERTY_ENABLE_XEP398 = "xmpp.avatarconversion.enabled";
    public static String PROPERTY_DELETE_OTHER_AVATAR = "xmpp.deleteotheravatar.enabled";

    //Constructors
    public PEPAvatar(String id, byte[] image, int height, int width, String mimetype)
    {
        this.id=id;
        this.image=image;
        this.height=height;
        this.width=width;
        this.mimetype=mimetype;
    }

    public PEPAvatar(String binval, String mimetype)
    {
        this.image=getImageFromBase64String(binval);
        this.mimetype=mimetype;
        this.init();
    }

    public PEPAvatar(String binval)
    {
        this.image=getImageFromBase64String(binval);
        this.init();
    }

    private PEPAvatar()
    {
    }

    //Getters and Setters
    public String getId()
    {
        return id;
    }

    private void setId(String id)
    {
        this.id = id;
    }

    public byte[] getImage()
    {
        return image;
    }

    public String getImageAsBase64String()
    {
        return getImageStringFromBytes();
    }

	private void setImage(byte[] image)
    {
        this.image = image;
    }

    private void setImage(String binval)
    {
        this.image=getImageFromBase64String(binval);
    }

    public int getHeight()
    {
        return height;
    }

    private void setHeight(int height)
    {
        this.height = height;
    }

    public int getWidth()
    {
        return width;
    }

    private void setWidth(int width)
    {
        this.width = width;
    }

    public String getMimetype()
    {
        return mimetype;
    }

    private void setMimetype(String mimetype)
    {
        this.mimetype = mimetype;
    }

    /**
     * Load the PEPAvatar using PEPService
     * @param username
     *            the user from which the avatar will be loaded
     * @return PEPAvatar or null if error occurs
     * */
    public static PEPAvatar load(String username)
	{
        PEPAvatar result = new PEPAvatar();

        PEPService pep;
        try
        {
            //get Users PEPService, build JID and load
            pep = result.getPEPFromUser(new JID(username+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain()));
        }
        catch (Exception e1)
        {
            Log.error("load: "+e1.getMessage());
            return null;
        }

        //Search for relevant nodes
        Node meta = pep.getNode("urn:xmpp:avatar:metadata");
        Node avatar = pep.getNode("urn:xmpp:avatar:data");

        //Check for pep nodes
        if (meta!=null&&avatar!=null)
        {
            //META
            int height=-1;
            int width=-1;
            String type=null;
            String data=null;
            String id = null;

            List<PublishedItem> items = meta.getPublishedItems();
            if (items!=null)
            {
                //search publisheditems for info nodes
                for (PublishedItem itm : items)
                {
                    Element payload = itm.getPayload();
                    if (payload!=null)
                    {
                        Element info = payload.element("info");
                        //use the first info node and break
                        if (info!=null)
                        {
                            height = Integer.parseInt(info.attributeValue("height"));
                            width = Integer.parseInt(info.attributeValue("width"));
                            type = info.attributeValue("type");
                            id=info.attributeValue("id");
                            break;
                        }
                    }
                }
            }

            items = avatar.getPublishedItems();
            if (items!=null)
            {
                //search publisheditems for the image
                for (PublishedItem itm : items)
                {
                    Element payload = itm.getPayload();
                    if (payload!=null)
                    {
                        String img = payload.getText();
                        //take the first payload node and break
                        if (img!=null)
                        {
                            data = img;
                            break;
                        }
                    }
                }
            }

            //build / init the object and return val otherwhise null
            if (data!=null&&type!=null)
            {
                result.setHeight(height);
                result.setWidth(width);
                result.setId(id);
                result.setMimetype(type);
                result.setImage(data);
                return result;
            }
        }

        return null;
    }

    private PEPService getPEPFromUser(JID jid) throws Exception
    {
        PEPServiceManager pepmgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
        if (pepmgr==null)
        {
            throw new Exception("PEPServiceManager not available");
        }

        PEPService pep = pepmgr.getPEPService(jid);

        if (pep!=null)
        {
            return pep;
        }
        else
        {
            throw new Exception("PEPService from "+jid.getNode()+" not available");
        }
    }

    private void init()
    {
        try
        {
            this.id=getSHA1Hash(this.image);
            if (this.id==null)
            {
                Log.error("Could not calc. image hash!");
            }
        }
        catch (NoSuchAlgorithmException e)
        {
            Log.error("Error while build SHA-1 HASH: "+e.getMessage());
            this.id=null;
        }

        BufferedImage img = getImageFromBytes();
        if (img!=null)
        {
            this.height= img.getHeight();
            this.width = img.getWidth();
        }
        else
        {
            Log.error("Could not set height/width of image");
        }

        if (this.mimetype==null)
        {
            try
            {
                this.mimetype=URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(this.image));
            }
            catch (IOException e)
            {
                Log.error("Could not set mime type of image");
            }
        }
    }

    /**
     * delete metadata node of a user
     * @param jid
     *            Jid from which the node will be deleted
     * */
    private void deleteOldMetadataNode(JID jid)
    {
        if (this.pep==null)
        {
            try
            {
                pep = getPEPFromUser(jid);
            }
            catch (Exception e1)
            {
                Log.error("saveAvatar: "+e1.getMessage());
                return;
            }
        }

        Node nmeta = this.pep.getNode(NAMESPACE_METADATA);

        if (nmeta!=null)
        {
            nmeta.delete();
        }
    }

    public void routeMetaDataToServer(String username)
    {
        routeMetaDataToServer(new JID(username+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain()));
    }

    /**
     * delete metadata node of a user and build a new one with data of this object
     * @param jid
     *            the jid to which the node will be created
     * */
    public void routeMetaDataToServer(JID jid)
    {
        deleteOldMetadataNode(jid);

        IQ metadataiq = new IQ(Type.set);
        metadataiq.setFrom(jid);
        metadataiq.setID(id);
        Element metapubsub = metadataiq.setChildElement("pubsub", NAMESPACE_PUBSUB);
        Element metapublish = metapubsub.addElement("publish");
        metapublish.addAttribute("node", NAMESPACE_METADATA);
        Element metaitem = metapublish.addElement("item");
        metaitem.addAttribute("id",id);
        Element metadata = metaitem.addElement("metadata",NAMESPACE_METADATA);
        Element metainfo = metadata.addElement("info");
        metainfo.addAttribute("bytes",String.valueOf(this.image.length));
        metainfo.addAttribute("id",id);
        metainfo.addAttribute("height",String.valueOf(this.height));
        metainfo.addAttribute("type",this.mimetype);
        metainfo.addAttribute("width",String.valueOf(this.width));

        XMPPServer.getInstance().getIQRouter().route(metadataiq);
    }

    /**
     * delete data node of a user
     * @param jid
     *            the jid from which the node will be deleted
     * */
    private void deleteOldDataNode(JID jid)
    {
        if (this.pep==null)
        {
            try
            {
                pep = getPEPFromUser(jid);
            }
            catch (Exception e1)
            {
                Log.error("saveAvatar: "+e1.getMessage());
                return;
            }
        }

        Node ndata = this.pep.getNode(NAMESPACE_DATA);

        if (ndata!=null)
        {
            ndata.delete();
        }
    }

    public static void deletePEPAvatar(String username)
    {
        deletePEPAvatar(new JID(username+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain()));
    }

    /**
     * deletes pep avatar of a user and send a presence broadcast
     * @param jid
     *            the jid from which the avatar will be deleted
     * */
    public static void deletePEPAvatar(JID jid)
    {
        PEPAvatar pavatar = new PEPAvatar();

        pavatar.deleteOldDataNode(jid);

        pavatar.deleteOldMetadataNode(jid);

        pavatar.broadcastPresenceUpdate(jid, false);
    }

    public void routeDataToServer(String username)
    {
        routeDataToServer(new JID(username+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain()));
    }

    /**
     * delete data node of a user and build a new one with data of this object
     * @param jid
     *            the jid to which the node will be created
     * */
    public void routeDataToServer(JID jid)
    {
        deleteOldDataNode(jid);

        IQ imagedata = new IQ(Type.set);
        imagedata.setFrom(jid);
        imagedata.setID(UUID.randomUUID().toString());
        Element pubsub = imagedata.setChildElement("pubsub", NAMESPACE_PUBSUB);
        Element publish = pubsub.addElement("publish");
        publish.addAttribute("node", NAMESPACE_DATA);
        Element item = publish.addElement("item");
        item.addAttribute("id",id);
        Element data = item.addElement("data",NAMESPACE_DATA);
        data.setText(getImageStringFromBytes());

        XMPPServer.getInstance().getIQRouter().route(imagedata);
    }

    public void broadcastPresenceUpdate(String username, boolean publish)
    {
        broadcastPresenceUpdate(new JID(username+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain()),  publish);
    }

    /**
     * send a Presence to the server, which routes it to subscribers
     * @param jid
     *            is the senders jid
     * @param publish whether a photo is published or not
     * */
    public void broadcastPresenceUpdate(JID jid, boolean publish)
    {
        User usr;
        try
        {
            usr = XMPPServer.getInstance().getUserManager().getUser(jid.getNode());
            Presence presenceStanza = XMPPServer.getInstance().getPresenceManager().getPresence(usr);
            presenceStanza.setID(UUID.randomUUID().toString());
            if (presenceStanza.getFrom()==null)
            {
                presenceStanza.setFrom(jid);
            }

            Element x = presenceStanza.getChildElement("x", NAMESPACE_VCARDUPDATE);
            if (x==null)
            {
                x=presenceStanza.addChildElement("x", NAMESPACE_VCARDUPDATE);
            }

            Element photo = x.element("photo");

            if (photo==null)
            {
                photo=x.addElement("photo");
            }

            if (JiveGlobals.getBooleanProperty(PROPERTY_DELETE_OTHER_AVATAR,false))
            {
                if (publish&&this.id!=null)
                {
                    photo.setText(this.id);
                }
            }
            else
            {
                String sha1=getSHA1FromShrinkedImage(this.mimetype,this.image);
                if (sha1!=null)
                {
                    photo.setText(sha1);
                }
            }

            XMPPServer.getInstance().getPresenceRouter().route(presenceStanza);

        }
        catch (UserNotFoundException e)
        {
            Log.error("Could not send presence: "+e.getMessage());
        }
    }
    
    public static String getSHA1FromShrinkedImage(String mimetype, byte[] image)
    {
        if (image==null||mimetype==null)
            return null;
        
        final Iterator it = ImageIO.getImageWritersByMIMEType( mimetype );
        if ( !it.hasNext() )
        {
            Log.debug( "Cannot resize avatar. No writers available for MIME type {}.", mimetype );
            return null;
        }
        final ImageWriter iw = (ImageWriter) it.next();

        // Extract the original avatar from the VCard.        
        final int targetDimension = JiveGlobals.getIntProperty( PhotoResizer.PROPERTY_TARGETDIMENSION, PhotoResizer.PROPERTY_TARGETDIMENSION_DEFAULT );
        final byte[] resized = PhotoResizer.cropAndShrink( image, targetDimension, iw );
        if (resized!=null)
        {
            try {
				return getSHA1Hash(resized);
			} catch (NoSuchAlgorithmException e) {
				return null;
			}
        }
        else
        	return null;
    }

    private BufferedImage getImageFromBytes()
    {
        try
        {
        	if (this.image!=null)
        		return ImageIO.read(new ByteArrayInputStream(this.image));
        	else
        	{
        		Log.error("Error while converting bytes to BufferedImage: no image in buffer");
        		return null;
        	}
        }
        catch (IOException e)
        {
            Log.error("Error while converting bytes to BufferedImage: "+e.getMessage());
            return null;
        }
    }

    private byte[] getImageFromBase64String(String binval)
    {
        return Base64.decode( binval );
    }

    private String getImageStringFromBytes()
    {
        return Base64.encodeBytes(this.image);
    }

    private static String  getSHA1Hash(byte[] convertme) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return byteArray2Hex(md.digest(convertme));
    }

    private static String byteArray2Hex(final byte[] hash)
    {
        Formatter formatter = new Formatter();
        try
        {
            for (byte b : hash)
            {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
        finally
        {
            formatter.close();
        }
    }
}
