/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.audit.spi;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.Auditor;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.IQ;
import org.dom4j.io.XMLWriter;
import org.dom4j.Element;
import org.dom4j.DocumentFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class AuditorImpl implements Auditor {

    private AuditManager auditManager;
    private File currentAuditFile;
    private Writer writer;
    private XMLWriter xmlWriter;
    private int maxSize;
    private long maxCount;
    private int logTimeout;
    private boolean closed = false;

    /**
     * Queue that holds the audited packets that will be later saved to an XML file.
     */
    private Queue<AuditPacket> logQueue = new LinkedBlockingQueue<AuditPacket>();

    /**
     * Timer to save queued logs to the XML file.
     */
    private Timer timer = new Timer();
    private SaveQueuedPacketsTask saveQueuedPacketsTask;

    public AuditorImpl(AuditManager manager) {
        auditManager = manager;
    }

    public void audit(Packet packet) {
        if (auditManager.isEnabled()) {
            if (packet instanceof Message) {
                if (auditManager.isAuditMessage()) {
                    writePacket(packet);
                }
            }
            else if (packet instanceof Presence) {
                if (auditManager.isAuditPresence()) {
                    writePacket(packet);
                }
            }
            else if (packet instanceof IQ) {
                if (auditManager.isAuditIQ()) {
                    writePacket(packet);
                }
            }
        }
    }

    public void stop() {
        // Stop the scheduled task for saving queued packets to the XML file
        timer.cancel();
        // Save all remaining queued packets to the XML file
        saveQueuedPackets();
        close();
    }

    private void close() {
        if (xmlWriter != null) {
            try {
                xmlWriter.flush();
                xmlWriter.close();
                writer.write("</jive>");
                writer.close();
                writer = null;
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void writePacket(Packet packet) {
        if (!closed) {
            // Add to the logging queue this new entry that will be saved later
            logQueue.add(new AuditPacket(packet.createCopy()));
        }
    }

    private void prepareAuditFile() throws IOException {
        if (currentAuditFile == null || currentAuditFile.length() > maxSize) {
            rotateFiles();
        }
    }

    protected void setMaxValues(int size, int count) {
        maxSize = size * 1024*1024;
        maxCount = count;
    }

    public void setLogTimeout(int newTimeout) {
        // Cancel any existing task because the timeout has changed
        if (saveQueuedPacketsTask != null) {
            saveQueuedPacketsTask.cancel();
        }
        this.logTimeout = newTimeout;
        // Create a new task and schedule it with the new timeout
        saveQueuedPacketsTask = new SaveQueuedPacketsTask();
        timer.schedule(saveQueuedPacketsTask, logTimeout, logTimeout);

    }

    public int getQueuedPacketsNumber() {
        return logQueue.size();
    }

    private void rotateFiles() throws IOException {
        close();
        int i;
        // Find the next available log file name
        for (i = 0; maxCount < 1 || i < maxCount; i++) {
            currentAuditFile = new File(JiveGlobals.getMessengerHome() + File.separator + "logs",
                    "jive.audit-" + i + ".log");
            if (!currentAuditFile.exists()) {
                break;
            }
        }
        // Two edge cases, i == 0 (no log files exist) and i == MAX_FILE_COUNT
        // If i == 0 then the loop above has already set currentAuditFile to
        // the correct file name, so we only need to setup a file name if i != 0
        if (i != 0) {
            if (i == maxCount) {
                // We need to delete the last in the series to make room for the next file
                // the currentAuditFile should be pointing at the last legitimate
                // file name in the series (i < MAX_FILE_COUNT) so we just delete it
                // so the previous file can be rotated to it
                currentAuditFile.delete();
            }
            // Rotate the files
            for (i--; i >= 0; i--) {
                String previousName = "jive.audit-" + i + ".log";
                File previousFile = new File(JiveGlobals.getMessengerHome() + File.separator + "logs",
                        previousName);
                previousFile.renameTo(currentAuditFile);
                currentAuditFile = new File(JiveGlobals.getMessengerHome() + File.separator + "logs",
                        previousName);
            }
        }

        writer = new FileWriter(currentAuditFile);
        writer.write("<jive xmlns=\"http://www.jivesoftware.org\">");
        xmlWriter = new XMLWriter(writer);
    }

    /**
     * Saves the queued entries to an XML file.
     */
    private class SaveQueuedPacketsTask extends TimerTask {
        public void run() {
            try {
                saveQueuedPackets();
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void saveQueuedPackets() {
        int batchSize = logQueue.size();
        for (int index = 0; index < batchSize; index++) {
            AuditPacket auditPacket = logQueue.poll();
            if (auditPacket != null) {
                try {
                    prepareAuditFile();
                    xmlWriter.write(auditPacket.getElement());
                }
                catch (IOException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    // Add again the entry to the queue to save it later
                    logQueue.add(auditPacket);
                }
            }
        }
        try {
            if (xmlWriter != null) {
                xmlWriter.flush();
            }
        }
        catch (IOException ioe) {

        }
    }

    /**
     * Wrapper on a Packet with information about the packet's status at the moment
     * when the message was queued.<p>
     *
     * The idea is to wrap every packet that is needed to be audited and then add the
     * wrapper to a queue that will be later processed (i.e. saved to the XML file).
     */
    private static class AuditPacket {

        private static DocumentFactory docFactory = DocumentFactory.getInstance();

        private Element element;

        public AuditPacket(Packet packet) {
            element = docFactory.createElement("packet", "http://www.jivesoftware.org");
            Session session = SessionManager.getInstance().getSession(packet.getFrom());
            if (session != null) {
                if (session.getStreamID() != null) {
                    element.addAttribute("streamID", session.getStreamID().toString());
                }
                switch (session.getStatus()) {
                    case Session.STATUS_AUTHENTICATED:
                        element.addAttribute("status", "auth");
                        break;
                    case Session.STATUS_CLOSED:
                        element.addAttribute("status", "closed");
                        break;
                    case Session.STATUS_CONNECTED:
                        element.addAttribute("status", "connected");
                        break;
                    case Session.STATUS_STREAMING:
                        element.addAttribute("status", "stream");
                        break;
                    default:
                        element.addAttribute("status", "unknown");
                        break;
                }
            }
            element.addAttribute("timestap", new Date().toString());
            element.add(packet.getElement());
        }

        /**
         * Returns the Element associated with this audit packet.
         *
         * @return the Element.
         */
        public Element getElement() {
            return element;
        }
    }
}