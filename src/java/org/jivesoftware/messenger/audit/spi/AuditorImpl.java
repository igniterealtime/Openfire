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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class AuditorImpl implements Auditor {

    private AuditManager auditManager;
    private File currentAuditFile;
    private Writer writer;
    private XMLStreamWriter xmlSerializer;
    private static final int MEGABYTE = 1024 * 1024;
    private int maxSize;
    private long maxCount;
    private boolean closed = false;

    public AuditorImpl(AuditManager manager) {
        auditManager = manager;
    }

    public void audit(XMPPPacket packet) {
        if (auditManager.isEnabled()) {
            if (packet instanceof Message) {
                if (auditManager.isEnabled() && auditManager.isAuditMessage()) {
                    writePacket(packet, false);
                }
            }
            else if (packet instanceof Presence) {
                if (auditManager.isEnabled() && auditManager.isAuditPresence()) {
                    writePacket(packet, false);
                }
            }
            else if (packet instanceof IQ) {
                if (auditManager.isEnabled() && auditManager.isAuditIQ()) {
                    writePacket(packet, false);
                }
            }
        }
    }

    public synchronized void audit(Message packet) {
        if (auditManager.isEnabled() && auditManager.isAuditMessage()) {
            writePacket(packet, false);
        }
    }

    public synchronized void audit(Presence packet, int transition) {
        if (auditManager.isEnabled() && auditManager.isAuditPresence()) {
            writePacket(packet, false);
        }
    }

    public synchronized void audit(IQ packet) {
        if (auditManager.isEnabled() && auditManager.isAuditIQ()) {
            writePacket(packet, false);
        }
    }

    public synchronized void auditDroppedPacket(XMPPPacket packet) {
        writePacket(packet, true);
    }

    public synchronized void audit(AuditEvent event) {
        try {
            prepareAuditFile();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void close() {
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

    private void writePacket(XMPPPacket packet, boolean dropped) {
        if (!closed) {
            try {
                prepareAuditFile();
                xmlSerializer.writeStartElement("packet");
                xmlSerializer.writeDefaultNamespace("http://jivesoftware.com");
                Session session = packet.getOriginatingSession();
                if (session != null) {
                    if (session.getStreamID() != null) {
                        xmlSerializer.writeAttribute("session", session.getStreamID().toString());
                    }
                    switch (session.getStatus()) {
                        case Session.STATUS_AUTHENTICATED:
                            xmlSerializer.writeAttribute("status", "auth");
                            break;
                        case Session.STATUS_CLOSED:
                            xmlSerializer.writeAttribute("status", "closed");
                            break;
                        case Session.STATUS_CONNECTED:
                            xmlSerializer.writeAttribute("status", "connected");
                            break;
                        case Session.STATUS_STREAMING:
                            xmlSerializer.writeAttribute("status", "stream");
                            break;
                        default:
                            xmlSerializer.writeAttribute("status", "unknown");
                            break;
                    }
                }
                xmlSerializer.writeAttribute("timestamp", new Date().toString());
                if (packet.isSending()) {
                    xmlSerializer.writeAttribute("sending", "true");
                }
                if (dropped) {
                    xmlSerializer.writeAttribute("dropped", "true");
                }
                packet.send(xmlSerializer, 0);
                xmlSerializer.writeEndElement();
                xmlSerializer.flush();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        } // closed
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
        xmlSerializer.writeStartElement("jive", "jive", "http://jivesoftware.com");
        xmlSerializer.writeNamespace("jive", "http://jivesoftware.com");
    }
}
