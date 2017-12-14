/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.text.ParseException;

import java.util.LinkedList;

/**
 * Write audio data to a file
 */
public class Recorder extends Thread {

    private BufferedOutputStream bo;
    private FileOutputStream fo;

    private static final int BUFFER_SIZE = 16 * 1024;

    private static String defaultRecordDirectory = ".";

    private String recordPath;
    private boolean recordRtp;

    private static String fileSeparator = System.getProperty("file.separator");

    private boolean done;

    public Recorder(String recordPath, String recordingType,
        MediaInfo mediaInfo) throws IOException {

    this(null, recordPath, recordingType, mediaInfo);
    }

    public Recorder(String recordDirectory, String recordPath, String recordingType,
        MediaInfo mediaInfo) throws IOException {

    this.recordPath = getAbsolutePath(recordDirectory, recordPath);

    if (recordingType.equalsIgnoreCase("Rtp")) {
        recordRtp = true;
    } else if (recordingType.equalsIgnoreCase("Au") == false) {
        throw new IOException("Invalid recording type " + recordingType);
    }

    openFile(mediaInfo);

        start();
    }

    public static String getAbsolutePath(String recordDirectory, String recordPath)
        throws IOException {

        String osName = System.getProperty("os.name");

        if (osName.indexOf("Windows") >= 0) {
        if (recordPath.substring(0,1).equals(fileSeparator) == true ||
                recordPath.charAt(1) == ':') {

        return recordPath;
        }
    } else {
        if (recordPath.substring(0,1).equals(fileSeparator) == true) {
        return recordPath;
        }
    }

    /*
     * Not absolute
     */
    if (recordDirectory == null) {
            recordDirectory = System.getProperty("com.sun.voip.server.Bridge.recordDirectory", defaultRecordDirectory);
    }

    String path = recordDirectory + fileSeparator + recordPath;

    try {
        checkPermission(path, false);
    } catch (ParseException e) {
        throw new IOException(e.getMessage());
    }

        return recordDirectory + fileSeparator + recordPath;
    }


    private void openFile(MediaInfo mediaInfo) throws IOException {
        File recordFile = new File(recordPath);

        try {
        synchronized(this) {
                if (recordFile.exists()) {
            recordFile.delete();
                }

                recordFile.createNewFile();

                fo = new FileOutputStream(recordFile);
                bo = new BufferedOutputStream(fo, BUFFER_SIZE);

                if (recordRtp == false) {
                    writeAuHeader(mediaInfo);
                } else {
                    /*
                     * Write RTP header
                     */
                    byte[] buf = new byte[16];
                    buf[0] = (byte) 0x52;  // R
                    buf[1] = (byte) 0x54;  // T
                    buf[2] = (byte) 0x50;  // P

                    bo.write(buf, 0, buf.length);
                }
        }
        } catch (IOException e) {
            fo = null;
            bo = null;
            Logger.error("can't create buffered output stream for " + recordPath
        + " " + e.getMessage());
            throw new IOException(
        "can't create buffered output stream for " + recordPath
            + " " + e.getMessage());
        }

    Logger.println("Recording to " + recordFile.getAbsolutePath()
        + " recording type is " + (recordRtp ? "RTP" : "Audio"));
    }

    public String getRecordPath() {
    return recordPath;
    }

    public static void checkPermission(String recordPath)
        throws ParseException {

    checkPermission(recordPath, false);
    }

    public static void checkPermission(String recordPath, boolean isDirectory)
        throws ParseException {

    if (isDirectory) {
        File file = new File(recordPath);

        if (file.exists() == false) {
            throw new ParseException(
            "Non-existent directory:  " + recordPath, 0);
        }

        if (file.isDirectory() == false) {
            throw new ParseException("Not a directory:  " + recordPath, 0);
        }

        if (file.canWrite() == false) {
            throw new ParseException("Permission denied.  Can't write "
            + recordPath, 0);
        }

        return;
    }

    File file = new File(recordPath);

    try {
        if (file.exists()) {
        if (file.isDirectory()) {
            throw new ParseException("Not a regular file:  "
            + recordPath + ".", 0);
        }
        }

        /*
         * Try to create a file in the directory
         */
        String directory = defaultRecordDirectory;

        int i = recordPath.lastIndexOf(fileSeparator);

        if (i > 0) {
        directory = recordPath.substring(0, i);
        }

        file = File.createTempFile("Record", "tmp", new File(directory));

        file.delete();
    } catch (IOException e) {
        throw new ParseException("Unable to create file " + recordPath
        + ".  " + e.getMessage(), 0);
        }
    }

    private byte[] auHeader;

    private void writeAuHeader(MediaInfo mediaInfo) throws IOException {
        /*
         * write a .au header to the file
         *
         * magic:      4 bytes    ".snd"
         * hdrsize:    4 bytes
         * datasize:   4 bytes    0
         * encoding:   4 bytes     1 for ulaw, 3 for linear
         * sampleRate: 4 bytes
         * channels:   4 bytes
         */
        auHeader = new byte[24];

        auHeader[0] = '.';
        auHeader[1] = 's';
        auHeader[2] = 'n';
        auHeader[3] = 'd';

        auHeader[7] = 24;

        int encoding = mediaInfo.getEncoding();

        if (encoding == RtpPacket.PCMU_ENCODING) {
            auHeader[15] = 1;
        } else {
            auHeader[15] = 3;
        }

        int sampleRate = mediaInfo.getSampleRate();

        auHeader[16] = (byte)((sampleRate >> 24) & 0xff);
        auHeader[17] = (byte)((sampleRate >> 16) & 0xff);
        auHeader[18] = (byte)((sampleRate >> 8) & 0xff);
        auHeader[19] = (byte)(sampleRate & 0xff);

        int channels = mediaInfo.getChannels();

        auHeader[20] = (byte)((channels >> 24) & 0xff);
        auHeader[21] = (byte)((channels >> 16) & 0xff);
        auHeader[22] = (byte)((channels >> 8) & 0xff);
        auHeader[23] = (byte)(channels & 0xff);

        bo.write(auHeader, 0, auHeader.length);
    }

    public void done() {
        if (done) {
            return;
        }

        done = true;

        synchronized(dataToWrite) {
            dataToWrite.notifyAll();
        }
    }

    /*
     * Write data to a file
     */
    private LinkedList dataToWrite = new LinkedList();

    class DataToWrite {
        public byte[] data;
    public int offset;
        public int length;

        public DataToWrite(byte[] data, int offset, int length) {
        /*
         * We have to copy the data, otherwise caller could
         * overwrite it.
         */
            this.data = new byte[length];
        this.offset = offset;
            this.length = length;

        System.arraycopy(data, offset, this.data, 0, length);
        }
    }

    private long lastWriteTime;

    public void writePacket(byte[] data, int offset, int dataLength)
        throws IOException {

    if (recordRtp) {
        byte[] buf = new byte[dataLength + 4];

            int timeChange;

        long now = System.currentTimeMillis();

            if (lastWriteTime == 0) {
                timeChange = 0;
            } else {
                timeChange = (int) (now - lastWriteTime);
            }

        lastWriteTime = now;

            buf[0] = (byte) ((buf.length >> 8) & 0xff);
            buf[1] = (byte) (buf.length & 0xff);
            buf[2] = (byte) ((timeChange >> 8) & 0xff);
            buf[3] = (byte) (timeChange & 0xff);

        System.arraycopy(data, offset, buf, 4, dataLength);
        write(buf, 0, buf.length);
    } else {
        write(data, offset, dataLength);
    }
    }

    public void write(int[] data, int offset, int length) throws IOException {
    byte[] byteData = new byte[length * 2];

    for (int i = 0; i < length; i++) {
        byteData[(2 * i)] = (byte) ((data[i + offset] >> 8) & 0xff);
        byteData[(2 * i) + 1] = (byte) (data[i + offset] & 0xff);
    }

        write(byteData, 0, byteData.length);
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        if (done) {
            return;
        }

        synchronized(dataToWrite) {
            dataToWrite.add(new DataToWrite(data, offset, length));
            dataToWrite.notifyAll();
        }
    }

    private int dataSize;

    public void run() {
    long lastWriteTime = 0;

        while (true) {
            synchronized(dataToWrite) {
        if (done) {
            break;
        }

                if (dataToWrite.size() == 0) {
                    try {
                        dataToWrite.wait();
                    } catch (InterruptedException e) {
                    }

            if (done) {
            break;
            }
        }

            while (dataToWrite.size() > 0) {
                writeData((DataToWrite) dataToWrite.remove(0));
        }

                continue;
        }
    }

    /*
     * Flush out remaining data
     */
    synchronized (dataToWrite) {
        while (dataToWrite.size() > 0) {
            writeData((DataToWrite) dataToWrite.remove(0));
        }
    }

    writeDataSize();
    }

    private void writeData(DataToWrite d) {
        try {
        synchronized(this) {
                bo.write(d.data, 0, d.length);
        dataSize += d.length;
            }
        } catch (IOException e) {
            Logger.println("Can't record to " + recordPath);
        done();
        }
    }

    private void writeDataSize() {
        try {
        synchronized(this) {
        if (bo != null) {
            bo.flush();
                    bo.close();
            fo.flush();
                    fo.close();
        }

        if (auHeader != null) {
            /*
             * Now write the data size in the auHeader
             */
            auHeader[8]  = (byte) ((dataSize >> 24) & 0xff);
            auHeader[9]  = (byte) ((dataSize >> 16) & 0xff);
            auHeader[10] = (byte) ((dataSize >> 8) & 0xff);
            auHeader[11] = (byte) (dataSize & 0xff);

            try {
                        RandomAccessFile raf = new RandomAccessFile(
                recordPath, "rw");

                raf.write(auHeader);
            raf.close();
            } catch (FileNotFoundException e) {
            Logger.println(
                "Unable to write data size to recording "
                + recordPath + " " + e.getMessage());
            }
        }
        }
        } catch (IOException e) {
        Logger.println("Exception closing recording " + recordPath
        + " " + e.getMessage());
        }
    }

    public static void setDefaultRecordingDirectory(
        String defaultRecordDirectory) throws ParseException {

    checkPermission(defaultRecordDirectory, true);

        Recorder.defaultRecordDirectory = defaultRecordDirectory;

        Logger.println("Default recording directory set to "
                + defaultRecordDirectory);
    }

    public static String getRecordingDirectory() {
        return defaultRecordDirectory;
    }

}
