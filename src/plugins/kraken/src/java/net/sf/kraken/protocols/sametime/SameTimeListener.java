/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.sametime;

import java.lang.ref.WeakReference;
import java.util.Vector;

import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.xmpp.packet.JID;

import com.lotus.sametime.awareness.AwarenessServiceEvent;
import com.lotus.sametime.awareness.AwarenessServiceListener;
import com.lotus.sametime.awareness.StatusEvent;
import com.lotus.sametime.awareness.StatusListener;
import com.lotus.sametime.buddylist.BLEvent;
import com.lotus.sametime.buddylist.BLServiceListener;
import com.lotus.sametime.community.LoginEvent;
import com.lotus.sametime.community.LoginListener;
import com.lotus.sametime.im.Im;
import com.lotus.sametime.im.ImEvent;
import com.lotus.sametime.im.ImListener;
import com.lotus.sametime.im.ImServiceListener;

/**
 * @author Daniel Henninger
 */
public class SameTimeListener implements LoginListener, ImServiceListener, ImListener, BLServiceListener, AwarenessServiceListener, StatusListener {

    static Logger Log = Logger.getLogger(SameTimeListener.class);
    
    private Vector<Im> imOpened = new Vector<Im>();

    SameTimeListener(SameTimeSession session) {
        this.sameTimeSessionRef = new WeakReference<SameTimeSession>(session);
    }

    WeakReference<SameTimeSession> sameTimeSessionRef;

    public SameTimeSession getSession() {
        return sameTimeSessionRef.get();
    }
    
    public Im getIMSession(JID jid) {
        Im currentIm = null;
        for (int i = 0; i < imOpened.size(); i++) {
            currentIm = imOpened.elementAt(i);
            if (getSession().getTransport().convertIDToJID(currentIm.getPartner().getName()).equals(jid)) {
                return currentIm;
            }
        }
        return null;
    }

    public void loggedIn(LoginEvent loginEvent) {
        Log.debug("SameTime: Successful login: "+loginEvent);
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
    }

    public void loggedOut(LoginEvent loginEvent) {
        Log.debug("SameTime: Logged out: "+loginEvent);        
    }

    public void imReceived(ImEvent imEvent) {
        Log.debug("SameTime: Received IM: "+imEvent);
        Im im = imEvent.getIm();
        boolean imExists = false;
        Im currentIm = null;
        
        for (int i = 0; i < imOpened.size(); i++) {
            currentIm = imOpened.elementAt(i);
            if(currentIm.equals(im)) {
                imExists = true;
                im = currentIm;
                break;
            }
        }
        
        if (!imExists) {
          imOpened.addElement(im);
          im.addImListener(this);
        }   
    }

    public void blRetrieveFailed(BLEvent blEvent) {
        Log.error("SameTime: Failed to retrieve buddy list: "+blEvent);
    }

    public void blRetrieveSucceeded(BLEvent blEvent) {
        Log.debug("SameTime: Got buddy list success event: "+blEvent);
    }

    public void blSetFailed(BLEvent blEvent) {
        Log.error("SameTime: Failed to set buddy list: "+blEvent);
    }

    public void blSetSucceeded(BLEvent blEvent) {
        Log.debug("SameTime: Buddy list set succeeded: "+blEvent);
    }

    public void blUpdated(BLEvent blEvent) {
        Log.debug("SameTime: Buddy list update succeeded: "+blEvent);
    }

    public void serviceAvailable(BLEvent blEvent) {
        // Buddy list service is available, ignore
    }

    public void serviceUnavailable(BLEvent blEvent) {
        // Buddy list service is unavailable, ignore
    }

    public void serviceAvailable(AwarenessServiceEvent awarenessServiceEvent) {
        // Awareness service is available, ignore
    }

    public void serviceUnavailable(AwarenessServiceEvent awarenessServiceEvent) {
        // Awareness service is unavailable, ignore
    }

    public void groupCleared(StatusEvent statusEvent) {
        Log.debug("SameTime: Status group cleared: "+statusEvent);
    }

    public void userStatusChanged(StatusEvent statusEvent) {
        Log.debug("SameTime: User statis has changed: "+statusEvent);
    }

    public void dataReceived(ImEvent imEvent) {
        Log.debug("SameTime: Data Received data type = " + imEvent.getDataType());
    }

    public void imClosed(ImEvent imEvent) {
        Log.debug("SameTime: Closed IM session: "+imEvent);
        Im im = imEvent.getIm();
        Im currentIm = null;
        
        for (int i = 0; i < imOpened.size(); i++) {
            currentIm = imOpened.elementAt(i);
            if (currentIm.equals(im)) {
                imOpened.removeElement(im);
                im.close(0);
                im.removeImListener(this);
                break;
            }
        }
    }

    public void imOpened(ImEvent imEvent) {
        // We are not doing anything when an IM session is opened
        Log.debug("SameTime: Opened IM session: "+imEvent);
    }

    public void openImFailed(ImEvent imEvent) {
        Log.error("SameTime: Failed to open IM session: "+imEvent);
    }

    public void textReceived(ImEvent imEvent) {
        Log.debug("SameTime: Received IM text: "+imEvent);
        getSession().getTransport().sendMessage(
            getSession().getJID(),
            getSession().getTransport().convertIDToJID(imEvent.getIm().getPartner().getName()),
            imEvent.getText()
        );
    }
    
}
