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
import org.jivesoftware.messenger.audit.AuditEvent;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.Auditor;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.IQ;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

public class AuditorImpl implements Auditor {

    private AuditManager auditManager;
    private File currentAuditFile;
    private Writer writer;
    private XMLStreamWriter xmlSerializer;
    private static final int MEGABYTE = 1024 * 1024;
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
                    writePacket(packet, false);
                }
            }
            else if (packet instanceof Presence) {
                if (auditManager.isAuditPresence()) {
                    writePacket(packet, false);
                }
            }
            else if (packet instanceof IQ) {
                if (auditManager.isAuditIQ()) {
                    writePacket(packet, false);
                }
            }
        }
    }

    /*public void auditDroppedPacket(XMPPPacket packet) {
        writePacket(packet, true);
    }

    public void audit(AuditEvent event) {
        // TODO Implement this functionality. Not used currently.
    }*/

    public void stop() {
        // Stop the scheduled task for saving queued packets to the XML file
        timer.cancel();
        // Save all remaining queued packets to the XML file
        saveQueuedPackets();
        close();
    }

    private void close() {
        if (xmlSerializer != null) {
            try {
                xmlSerializer.writeEndElement();
                xmlSerializer.flush();
                xmlSerializer = null;
                writer.close();
                writer = null;
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void writePacket(Packet packet, boolean dropped) {
        if (!closed) {
            // Add to the logging queue this new entry that will be saved later
            logQueue.add(new AuditPacket((Packet) packet.createDeepCopy(), dropped));
        }
    }

    private void prepareAuditFile() throws IOException, XMLStreamException {
        if (currentAuditFile == null || currentAuditFile.length() > maxSize) {
            rotateFiles();
        }
    }

    protected void setMaxValues(int size, int count) {
        maxSize = size * MEGABYTE;
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

    private void rotateFiles() throws IOException, XMLStreamException {
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
        xmlSerializer = XMLOutputFactory.newInstance().createXMLStreamWriter(writer);
        xmlSerializer.setDefaultNamespace("jabber:client");
        xmlSerializer.writeStartElement("jive", "jive", "http://jivesoftware.org");
        xmlSerializer.writeNamespace("jive", "http://jivesoftware.org");
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
        AuditPacket entry;
        int batchSize = logQueue.size();
        for (int index = 0; index < batchSize; index++) {
            entry = logQueue.poll();
            if (entry != null) {
                try {
                    prepareAuditFile();
                    entry.send(xmlSerializer);
                    xmlSerializer.flush();
                }
                catch (IOException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    // Add again the entry to the queue to save it later
                    logQueue.add(entry);
                }
                catch (XMLStreamException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    // Add again the entry to the queue to save it later
                    logQueue.add(entry);
                }
            }
        }
    }

    /**
     * Wrapper on a Packet with information about the packet's status at the moment when the message
     * was queued.<p>
     *
     * The idea is to wrap every packet that is needed to be audited and then add the wrapper to a
     * queue that will be later processed (i.e. saved to the XML file).
     */
    private class AuditPacket {
        private Packet packet;
        private String streamID;
        private String sessionStatus;
        private Date timestamp;
        private boolean sending;
        private boolean dropped;

        public AuditPacket(Packet packet, boolean dropped) {
            this.packet = packet;
            this.dropped = dropped;
            this.timestamp = new Date();
            this.sending = packet.isSending();
            Session session = packet.getOriginatingSession();
            if (session != null) {
                if (session.getStreamID() != null) {
                    this.streamID = session.getStreamID().toString();
                }
                switch (session.getStatus()) {
                    case Session.STATUS_AUTHENTICATED:
                        this.sessionStatus =  "auth";
                        break;
                    case Session.STATUS_CLOSED:
                        this.sessionStatus = "closed";
                        break;
                    case Session.STATUS_CONNECTED:
                        this.sessionStatus = "connected";
                        break;
                    case Session.STATUS_STREAMING:
                        this.sessionStatus = "stream";
                        break;
                    default:
                        this.sessionStatus = "unknown";
                        break;
                }
            }
        }

        public void send(XMLStreamWriter xmlSerializer) {
            try {
                xmlSerializer.writeStartElement("packet");
                xmlSerializer.writeDefaultNamespace("http://jivesoftware.org");

                if (streamID != null) {
                    xmlSerializer.writeAttribute("session", streamID);
                }
                if (sessionStatus != null) {
                    xmlSerializer.writeAttribute("status", sessionStatus);
                }
                xmlSerializer.writeAttribute("timestamp", timestamp.toString());
                if (sending) {
                    xmlSerializer.writeAttribute("sending", "true");
                }
                if (dropped) {
                    xmlSerializer.writeAttribute("dropped", "true");
                }
                packet.send(xmlSerializer, 0);
                xmlSerializer.writeEndElement();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }

    }

}
