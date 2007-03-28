/**
 * $RCSfile$
 * $Revision: 3186 $
 * $Date: 2005-12-11 00:07:52 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.audit.spi;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.audit.AuditManager;
import org.jivesoftware.openfire.audit.Auditor;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AuditorImpl implements Auditor {

    private AuditManager auditManager;
    private File currentAuditFile;
    private Writer writer;
    private org.jivesoftware.util.XMLWriter xmlWriter;
    /**
     * Limit date used to detect when we need to rollover files. This date will be
     * configured as the last second of the day.
     */
    private Date currentDateLimit;
    /**
     * Max size in bytes that all audit log files may have. When the limit is reached
     * oldest audit log files will be removed until total size is under the limit.
     */
    private int maxTotalSize;
    /**
     * Max size in bytes that each audit log file may have. Once the limit has been
     * reached a new audit file will be created.
     */
    private int maxFileSize;
    /**
     * Max number of days to keep audit information. Once the limit has been reached
     * audit files that contain information that exceed the limit will be deleted.
     */
    private int maxDays;
    /**
     * Flag that indicates if packets can still be accepted to be saved to the audit log.
     */
    private boolean closed = false;
    /**
     * Directoty (absolute path) where the audit files will be saved.
     */
    private String logDir;
    /**
     * File (or better say directory) of the folder that contains the audit logs.
     */
    private File baseFolder;

    /**
     * Queue that holds the audited packets that will be later saved to an XML file.
     */
    private BlockingQueue<AuditPacket> logQueue = new LinkedBlockingQueue<AuditPacket>();

    /**
     * Timer to save queued logs to the XML file.
     */
    private Timer timer = new Timer("Auditor");
    private SaveQueuedPacketsTask saveQueuedPacketsTask;
    private FastDateFormat dateFormat;
    private static FastDateFormat auditFormat;

    public AuditorImpl(AuditManager manager) {
        auditManager = manager;
        dateFormat = FastDateFormat.getInstance("yyyyMMdd", TimeZone.getTimeZone("UTC"));
        auditFormat = FastDateFormat.getInstance("MMM dd, yyyy hh:mm:ss:SSS a", JiveGlobals.getLocale());
    }

    protected void setMaxValues(int totalSize, int fileSize, int days) {
        maxTotalSize = totalSize * 1024*1024;
        maxFileSize = fileSize * 1024*1024;
        maxDays = days;
    }

    public void setLogTimeout(int logTimeout) {
        // Cancel any existing task because the timeout has changed
        if (saveQueuedPacketsTask != null) {
            saveQueuedPacketsTask.cancel();
        }
        // Create a new task and schedule it with the new timeout
        saveQueuedPacketsTask = new SaveQueuedPacketsTask();
        timer.schedule(saveQueuedPacketsTask, logTimeout, logTimeout);

    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
        // Create and catch file of the base folder that will contain audit files
        baseFolder = new File(logDir);
        // Create the folder if it does not exist
        if (!baseFolder.exists()) {
            baseFolder.mkdir();
        }
    }

    public int getQueuedPacketsNumber() {
        return logQueue.size();
    }

    public void audit(Packet packet, Session session) {
        if (auditManager.isEnabled()) {
            if (packet instanceof Message) {
                if (auditManager.isAuditMessage()) {
                    writePacket(packet, session);
                }
            }
            else if (packet instanceof Presence) {
                if (auditManager.isAuditPresence()) {
                    writePacket(packet, session);
                }
            }
            else if (packet instanceof IQ) {
                if (auditManager.isAuditIQ()) {
                    writePacket(packet, session);
                }
            }
        }
    }

    private void writePacket(Packet packet, Session session) {
        if (!closed) {
            // Add to the logging queue this new entry that will be saved later
            logQueue.add(new AuditPacket(packet.createCopy(), session));
        }
    }

    public void stop() {
        // Stop queuing packets since we are being stopped
        closed = true;
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
                writer.write("</jive>");
                xmlWriter.close();
                writer = null;
                xmlWriter = null;
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void prepareAuditFile(Date auditDate) throws IOException {
        ensureMaxTotalSize();

        // Rotate file if: we just started, current file size exceeded limit or date has changed
        if (currentAuditFile == null || currentAuditFile.length() > maxFileSize ||
                xmlWriter == null || currentDateLimit == null || auditDate.after(currentDateLimit))
        {
            createAuditFile(auditDate);
        }
    }

    /**
     * Ensures that max total size limit is not exceeded. If total size of audit files
     * exceed the limit then oldest audit files will be removed until total size does
     * not exceed limit.
     */
    private void ensureMaxTotalSize() {
        // Get list of existing audit files
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("jive.audit-") && name.endsWith(".log");
            }
        };
        File[] files = baseFolder.listFiles(filter);
        long totalLength = 0;
        for (File file : files) {
            totalLength = totalLength + file.length();
        }
        // Check if total size has been exceeded
        if (totalLength > maxTotalSize) {
            // Sort files by name (chronological order)
            List<File> sortedFiles = new ArrayList<File>(Arrays.asList(files));
            Collections.sort(sortedFiles, new Comparator<File>() {
                public int compare(File o1, File o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            // Delete as many old files as required to be under the limit
            while (totalLength > maxTotalSize && !sortedFiles.isEmpty()) {
                File fileToDelete = sortedFiles.remove(0);
                totalLength = totalLength - fileToDelete.length();
                if (fileToDelete.equals(currentAuditFile)) {
                    // Close current file
                    close();
                }
                // Delete oldest file
                fileToDelete.delete();
            }
        }
    }

    /**
     * Deletes old audit files that exceeded the max number of days limit.
     */
    private void ensureMaxDays() {
        if (maxDays == -1) {
            // Do nothing since we don't have any limit
            return;
        }

        // Set limit date after which we need to delete old audit files
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, maxDays * -1);

        final String oldestFile =
                "jive.audit-" + dateFormat.format(calendar.getTime()) + "-000.log";

        // Get list of audit files to delete
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("jive.audit-") && name.endsWith(".log") &&
                        name.compareTo(oldestFile) < 0;
            }
        };
        File[] files = baseFolder.listFiles(filter);
        // Delete old audit files
        for (File fileToDelete : files) {
            if (fileToDelete.equals(currentAuditFile)) {
                // Close current file
                close();
            }
            fileToDelete.delete();
        }
    }

    private void createAuditFile(Date auditDate) throws IOException {
        close();
        if (currentDateLimit == null || auditDate.after(currentDateLimit)) {
            // Set limit date after which we need to rollover the audit file (based on the date)
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(auditDate);
            calendar.set(Calendar.HOUR, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            currentDateLimit = calendar.getTime();
        }

        final String filePrefix = "jive.audit-" + dateFormat.format(auditDate) + "-";
        // Get list of existing audit files
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(filePrefix) && name.endsWith(".log");
            }
        };
        File[] files = baseFolder.listFiles(filter);
        if (files.length == 0) {
            // This is the first audit file for the day
            currentAuditFile = new File(logDir, filePrefix + "000.log");
        }
        else {
            // Search the last index used for the day
            File lastFile = files[files.length - 1];
            StringTokenizer tokenizer = new StringTokenizer(lastFile.getName(), "-.");
            // Skip "jive"
            tokenizer.nextToken();
            // Skip "audit"
            tokenizer.nextToken();
            // Skip "date"
            tokenizer.nextToken();
            int index = Integer.parseInt(tokenizer.nextToken()) + 1;
            if (index > 999) {
                Log.warn("Failed to created audit file. Max limit of 999 files has been reached " +
                        "for the date: " + dateFormat.format(auditDate));
                return;
            }
            currentAuditFile = new File(logDir,
                    filePrefix + StringUtils.zeroPadString(Integer.toString(index), 3) + ".log");
        }


        // Find the next available log file name
        /*for (int i = 0; i < 1000; i++) {
            currentAuditFile = new File(logDir,
                    filePrefix + StringUtils.zeroPadString(Integer.toString(i), 3) + ".log");
            if (!currentAuditFile.exists()) {
                break;
            }
        }

        if (currentAuditFile == null) {
            Log.warn("Audit log not create since there are more than 999 files for the date: " +
                    dateFormat.format(auditDate));
            return;
        }*/

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentAuditFile), "UTF-8"));
        writer.write("<jive xmlns=\"http://www.jivesoftware.org\">");
        xmlWriter = new org.jivesoftware.util.XMLWriter(writer);
    }

    /**
     * Saves the queued entries to an XML file and checks that very old files are deleted.
     */
    private class SaveQueuedPacketsTask extends TimerTask {
        public void run() {
            try {
                // Ensure that saved audit logs are not too old
                ensureMaxDays();
                // Save queued packets to the audit logs
                saveQueuedPackets();
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void saveQueuedPackets() {
        List<AuditPacket> packets = new ArrayList<AuditPacket>(logQueue.size());
        logQueue.drainTo(packets);
        for (AuditPacket auditPacket : packets) {
            try {
                prepareAuditFile(auditPacket.getCreationDate());
                Element element = auditPacket.getElement();
                // Protect against null elements.
                if (element != null) {
                    xmlWriter.write(element);
                }
            }
            catch (IOException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                // Add again the entry to the queue to save it later
                if (xmlWriter != null) {
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
            Log.error(ioe);
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
        private Date creationDate;

        public AuditPacket(Packet packet, Session session) {
            element = docFactory.createElement("packet", "http://www.jivesoftware.org");
            creationDate = new Date();
            if (session != null && session.getStreamID() != null) {
                element.addAttribute("streamID", session.getStreamID().toString());
            }
            switch (session == null ? 0 : session.getStatus()) {
                case Session.STATUS_AUTHENTICATED:
                    element.addAttribute("status", "auth");
                    break;
                case Session.STATUS_CLOSED:
                    element.addAttribute("status", "closed");
                    break;
                case Session.STATUS_CONNECTED:
                    element.addAttribute("status", "connected");
                    // This is a workaround. Since we don't want to have an incorrect FROM attribute
                    // value we need to clean up the FROM attribute. The FROM attribute will contain
                    // an incorrect value since we are setting a fake JID until the user actually
                    // authenticates with the server.
                    packet.setFrom((String) null);
                    break;
                case Session.STATUS_STREAMING:
                    element.addAttribute("status", "stream");
                    break;
                default:
                    element.addAttribute("status", "unknown");
                    break;
            }
            element.addAttribute("timestamp", auditFormat.format(creationDate));
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

        /**
         * Returns the date when the packet was audited. This is the time when the
         * packet was queued to be saved.
         *
         * @return the date when the packet was audited.
         */
        public Date getCreationDate() {
            return creationDate;
        }
    }
}