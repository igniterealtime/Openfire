/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.audit.spi;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.jivesoftware.openfire.audit.AuditManager;
import org.jivesoftware.openfire.audit.Auditor;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AuditorImpl implements Auditor {

    private static final Logger Log = LoggerFactory.getLogger(AuditorImpl.class);

    private final AuditManager auditManager;
    private File currentAuditFile;
    private Writer writer;
    private org.jivesoftware.util.XMLWriter xmlWriter;
    /**
     * Limit date used to detect when we need to rollover files. This date will be
     * configured as the last second of the day.
     */
    private LocalDateTime currentDateLimit;
    /**
     * Max size in bytes that all audit log files may have. When the limit is reached
     * the oldest audit log files will be removed until total size is under the limit.
     */
    private long maxTotalSize;
    /**
     * Max size in bytes that each audit log file may have. Once the limit has been
     * reached a new audit file will be created.
     */
    private long maxFileSize;
    /**
     * Maximum time to keep audit information. Once the limit has been reached
     * audit files that contain information that exceed the limit will be deleted.
     */
    private Duration retention;
    /**
     * Flag that indicates if packets can still be accepted to be saved to the audit log.
     */
    private boolean closed = false;
    /**
     * Directory (absolute path) where the audit files will be saved.
     */
    private String logDir;
    /**
     * File (or better say directory) of the folder that contains the audit logs.
     */
    private File baseFolder;

    /**
     * Queue that holds the audited packets that will be later saved to an XML file.
     */
    private final BlockingQueue<AuditPacket> logQueue = new LinkedBlockingQueue<>();

    /**
     * Allow only a limited number of files for each day, max. three digits (000-999)
     */
    private final int maxTotalFilesDay = 1000;
    /**
     * Track the current index number `...-nnn.log´
     */
    private int filesIndex = 0;
    /**
     * Timer to save queued logs to the XML file.
     */
    private SaveQueuedPacketsTask saveQueuedPacketsTask;
    private final DateTimeFormatter dateFormat;
    private static DateTimeFormatter auditFormat;

    public AuditorImpl(AuditManager manager) {
        auditManager = manager;
        dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
        auditFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss:SSS a", JiveGlobals.getLocale()).withZone(JiveGlobals.getTimeZone().toZoneId());
    }

    protected void setMaxValues(int totalSize, int fileSize, Duration duration) {
        maxTotalSize = (long) totalSize * 1024L * 1024L;
        maxFileSize = (long) fileSize * 1024L * 1024L;
        retention = duration;
    }

    public void setLogTimeout(Duration logTimeout) {
        // Cancel any existing task because the timeout has changed
        if (saveQueuedPacketsTask != null) {
            saveQueuedPacketsTask.cancel();
        }
        // Create a new task and schedule it with the new timeout
        saveQueuedPacketsTask = new SaveQueuedPacketsTask();
        TaskEngine.getInstance().schedule(saveQueuedPacketsTask, logTimeout, logTimeout);

    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
        // Create and catch file of the base folder that will contain audit files
        baseFolder = new File(logDir);
        // Create the folder if it does not exist
        if (!baseFolder.exists()) {
            if ( !baseFolder.mkdir() ) {
                Log.error( "Unable to create log directory: {}", baseFolder );
            }
        }
    }

    @Override
    public int getQueuedPacketsNumber() {
        return logQueue.size();
    }

    @Override
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

    @Override
    public void stop() {
        // Stop queuing packets since we are being stopped
        closed = true;
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

    private void prepareAuditFile(Instant auditDate) throws IOException {
        ensureMaxTotalSize();

        // Rotate file if: we just started, current file size exceeded limit or date has changed
        if (currentAuditFile == null || currentAuditFile.length() > maxFileSize ||
                xmlWriter == null || currentDateLimit == null || auditDate.isAfter(currentDateLimit.atZone(JiveGlobals.getTimeZone().toZoneId()).toInstant()))
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
        FilenameFilter filter = (dir, name) -> name.startsWith("jive.audit-") && name.endsWith(".log");
        File[] files = baseFolder.listFiles(filter);
        if (files == null) {
            Log.debug( "Path '{}' does not denote a directory, or an IO exception occurred while trying to list its content.", baseFolder );
            return;
        }
        long totalLength = 0;
        for (File file : files) {
            totalLength = totalLength + file.length();
        }
        // Check if total size has been exceeded
        if (totalLength > maxTotalSize) {
            // Sort files by name (chronological order)
            List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
            sortedFiles.sort(Comparator.comparing(File::getName));
            // Delete as many old files as required to be under the limit
            while (totalLength > maxTotalSize && !sortedFiles.isEmpty()) {
                File fileToDelete = sortedFiles.remove(0);
                totalLength = totalLength - fileToDelete.length();
                if (fileToDelete.equals(currentAuditFile)) {
                    // Close current file
                    close();
                }
                // Delete oldest file
                if ( !fileToDelete.delete() )
                {
                    Log.warn( "Unable to delete file '{}' as part of regular log rotation based on size of files (Openfire failed to clean up after itself)!", fileToDelete );
                }
            }
        }
    }

    /**
     * Deletes old audit files that exceeded the max number of days limit.
     */
    private void ensureMaxDays() {
        if (retention.isNegative()) {
            // Do nothing since we don't have any limit
            return;
        }

        // Set limit date after which we need to delete old audit files
        Instant cutoff = Instant.now().minus(retention);

        final String oldestFile = "jive.audit-" + dateFormat.format(cutoff) + "-000.log";

        // Get list of audit files to delete
        FilenameFilter filter = (dir, name) -> name.startsWith("jive.audit-") && name.endsWith(".log") &&
                name.compareTo(oldestFile) < 0;

        final File[] files = baseFolder.listFiles(filter);
        if ( files != null )
        {
            // Delete old audit files
            for ( File fileToDelete : files )
            {
                if ( fileToDelete.equals( currentAuditFile ) )
                {
                    // Close current file
                    close();
                }
                if ( !fileToDelete.delete() )
                {
                    Log.warn( "Unable to delete file '{}' as part of regular log rotation based on age of file. (Openfire failed to clean up after itself)!", fileToDelete );
                }
            }
        }
    }

    /* if this new logic still causes problems one may want to 
    * use log4j or change the file format from YYYYmmdd-nnn to YYYYmmdd-HHMM */
    /**
    * Sets <b>xmlWriter</b> so this class can use it to write audit logs<br>
    * The audit filename <b>currentAuditFile</b> will be `jive.audit-YYYYmmdd-nnn.log´<br>
    * `nnn´ will be reset to `000´ when a new log file is created the next day <br>
    * `nnn´ will be increased for log files which belong to the same day<br>
    * <b>WARNING:</b> If log files of the current day are deleted and the server is restarted then
    * the value of `nnn´ may be random (it's calculated by `Math.max(files.length, filesIndex);´
    * with `filesIndex=0´ and  `files.length=nr(existing jive.audit-YYYYmmdd-???.log files)´ - 
    * if there are 10 audit files (033-043) then nnn will be 10 instead of 44).<br>
    * If  `nnn=999´ then all audit data will be written to this file till the next day.<br>
    * @param auditDate The date for which to write an audit file
    * @throws IOException On any problem writing the file.
    */
    private void createAuditFile(Instant auditDate) throws IOException {
        final String filePrefix = "jive.audit-" + dateFormat.format(auditDate) + "-";
        if (currentDateLimit == null || auditDate.isAfter(currentDateLimit.atZone(JiveGlobals.getTimeZone().toZoneId()).toInstant())) {
        // Set limit date after which we need to roll over the audit file (based on the date)
        currentDateLimit = auditDate.atZone(JiveGlobals.getTimeZone().toZoneId()).toLocalDate().atTime(LocalTime.MAX);

        filesIndex = 0;
    }
    // Get list of existing audit files
    FilenameFilter filter = (dir, name) -> name.startsWith(filePrefix) && name.endsWith(".log");
    File[] files = baseFolder.listFiles(filter);
    // if some daily files were already deleted then files.length will be smaller than filesIndex
    // see also WARNING above
    filesIndex = Math.max(files == null ? 0 : files.length, filesIndex);
        if (filesIndex >= maxTotalFilesDay)
        {
            // don't close this file, continue auditing to it
            return;
        }
        File tmpAuditFile = new File(logDir, filePrefix + StringUtils.zeroPadString(Integer.toString(filesIndex), 3) + ".log");
        if ( (filesIndex == maxTotalFilesDay-1) && !tmpAuditFile.exists() ) 
        {
            Log.warn("Creating last audit file for this date: " + dateFormat.format(auditDate));
        }
        while ( (filesIndex<(maxTotalFilesDay-1)) && (tmpAuditFile.exists()) )
        {
            Log.debug("Audit file '"+ tmpAuditFile.getName() +"' does already exist.");
            filesIndex++;
            tmpAuditFile = new File(logDir, filePrefix + StringUtils.zeroPadString(Integer.toString(filesIndex), 3) + ".log");
        }
        currentAuditFile = tmpAuditFile;
        close();
        // always append to an existing file (after restart)
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentAuditFile, true), StandardCharsets.UTF_8));
        writer.write("<jive xmlns=\"http://www.jivesoftware.org\">");
        xmlWriter = new org.jivesoftware.util.XMLWriter(writer);
    }

    /**
     * Saves the queued entries to an XML file and checks that very old files are deleted.
     */
    private class SaveQueuedPacketsTask extends TimerTask {
        @Override
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
        List<AuditPacket> packets = new ArrayList<>(logQueue.size());
        logQueue.drainTo(packets);
        for (AuditPacket auditPacket : packets) {
            try {
                prepareAuditFile(auditPacket.getCreation());
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
            Log.error(ioe.getMessage(), ioe);
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

        private static final DocumentFactory docFactory = DocumentFactory.getInstance();

        private final Element element;
        private final Instant creation;

        public AuditPacket(Packet packet, Session session) {
            element = docFactory.createElement("packet", "http://www.jivesoftware.org");
            creation = Instant.now();
            if (session != null && session.getStreamID() != null) {
                element.addAttribute("streamID", session.getStreamID().toString());
            }
            if (session == null) {
                element.addAttribute("status", "unknown");
            } else {
                switch (session.getStatus()) {
                    case AUTHENTICATED:
                        element.addAttribute("status", "auth");
                        break;
                    case CLOSED:
                        element.addAttribute("status", "closed");
                        break;
                    case CONNECTED:
                        element.addAttribute("status", "connected");
                        // This is a workaround. Since we don't want to have an incorrect FROM attribute
                        // value we need to clean up the FROM attribute. The FROM attribute will contain
                        // an incorrect value since we are setting a fake JID until the user actually
                        // authenticates with the server.
                        packet.setFrom((String) null);
                        break;
                    default:
                        element.addAttribute("status", "unknown");
                        break;
                }
            }
            element.addAttribute("timestamp", auditFormat.format(creation));
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
        public Instant getCreation() {
            return creation;
        }
    }
}
