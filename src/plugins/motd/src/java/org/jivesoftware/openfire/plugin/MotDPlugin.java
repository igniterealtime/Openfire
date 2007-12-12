package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.TimerTask;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * MotD (Message of the Day) plugin.
 * 
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class MotDPlugin implements Plugin {
   private static final String SUBJECT = "plugin.motd.subject";
   private static final String MESSAGE = "plugin.motd.message";
   private static final String ENABLED = "plugin.motd.enabled";

   private JID serverAddress;
   private MessageRouter router;

   private MotDSessionEventListener listener = new MotDSessionEventListener();

   public void initializePlugin(PluginManager manager, File pluginDirectory) {
      serverAddress = new JID(XMPPServer.getInstance().getServerInfo().getName());
      router = XMPPServer.getInstance().getMessageRouter();

      SessionEventDispatcher.addListener(listener);
   }

   public void destroyPlugin() {
      SessionEventDispatcher.removeListener(listener);

      listener = null;
      serverAddress = null;
      router = null;
   }

   public void setSubject(String message) {
      JiveGlobals.setProperty(SUBJECT, message);
   }

   public String getSubject() {
      return JiveGlobals.getProperty(SUBJECT, "Message of the Day");
   }

   public void setMessage(String message) {
      JiveGlobals.setProperty(MESSAGE, message);
   }

   public String getMessage() {
      return JiveGlobals.getProperty(MESSAGE, "Big Brother is watching.");
   }

   public void setEnabled(boolean enable) {
      JiveGlobals.setProperty(ENABLED, enable ? Boolean.toString(true) : Boolean.toString(false));
   }

   public boolean isEnabled() {
      return JiveGlobals.getBooleanProperty(ENABLED, false);
   }

   private class MotDSessionEventListener implements SessionEventListener {
      public void sessionCreated(Session session) {         
         if (isEnabled()) {
            final Message message = new Message();
            message.setTo(session.getAddress());
            message.setFrom(serverAddress);
            message.setSubject(getSubject());
            message.setBody(getMessage());

            TimerTask messageTask = new TimerTask() {
               public void run() {
                  router.route(message);
               }
            };

            TaskEngine.getInstance().schedule(messageTask, 5000);
         }
      }

      public void sessionDestroyed(Session session) {
         //ignore
      }

      public void anonymousSessionCreated(Session session) {
         //ignore
      }

      public void anonymousSessionDestroyed(Session session) {
         //ignore
      }
   }
}
