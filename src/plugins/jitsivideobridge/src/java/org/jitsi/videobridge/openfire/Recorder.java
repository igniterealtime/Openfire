/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.videobridge.openfire;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.ebml.*;
import org.ebml.io.*;
import org.ebml.matroska.*;
import org.ebml.util.*;

import org.slf4j.*;
import org.slf4j.Logger;

import java.text.ParseException;

import java.util.LinkedList;

/**
 * Write audio data to a file
 */
public class Recorder extends Thread
{
    private static final Logger Log = LoggerFactory.getLogger(Recorder.class);
    private BufferedOutputStream bo;
    private FileOutputStream fo;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static String defaultRecordDirectory = ".";
    private String recordPath;
    private String recordDirectory;
    private boolean recordRtp;
    private boolean recordWebm;
    private boolean recordAu;
    private static String fileSeparator = System.getProperty("file.separator");
    private boolean done;
	private boolean pcmu;
	private int sampleRate;
	private int channels;
	private long lastTimecode = 0;
	private MatroskaFileWriter mFW;
	private FileDataWriter iFW;


    public Recorder(String recordDirectory, String fileName, String recordingType, boolean pcmu, int sampleRate, int channels) throws IOException
    {
		this.recordPath = getAbsolutePath(recordDirectory, fileName);
		this.recordDirectory = recordDirectory;
		this.pcmu = pcmu;
		this.sampleRate = sampleRate;
		this.channels = channels;

		if (recordingType.equalsIgnoreCase("Rtp")) {
			recordRtp = true;
			openFile();

		} else if (recordingType.equalsIgnoreCase("webm")) {
			recordWebm = true;
			openWebmFile();

		} else if (recordingType.equalsIgnoreCase("Au")) {
			recordAu = true;
			openFile();

		} else {
			throw new IOException("Invalid recording type " + recordingType);
		}

		start();
    }

    public static String getAbsolutePath(String recordDirectory, String recordPath)
	    throws IOException {

        String osName = System.getProperty("os.name");

        if (osName.indexOf("Windows") >= 0)
        {
	    	if (recordPath.substring(0,1).equals(fileSeparator) == true || recordPath.charAt(1) == ':')
	    	{
				return recordPath;
	    	}

		} else {

	    	if (recordPath.substring(0,1).equals(fileSeparator) == true)
	    	{
				return recordPath;
	    	}
		}

		String path = recordDirectory + fileSeparator + recordPath;

		try {
			checkPermission(path, false);
		} catch (ParseException e) {
			throw new IOException(e.getMessage());
		}

        return recordDirectory + fileSeparator + recordPath;
    }


    private void openWebmFile() throws IOException
    {
		iFW = new FileDataWriter(recordPath);
		mFW = new MatroskaFileWriter(iFW);

		mFW.writeEBMLHeader();
		mFW.writeSegmentHeader();
		mFW.writeSegmentInfo();

		MatroskaFileTrack track = new MatroskaFileTrack();
		track.TrackNo = (short)1;
		track.TrackUID = new java.util.Random().nextLong();
		track.TrackType = MatroskaDocType.track_video;
		track.Name = "VP8";
		track.CodecName = "VP8";
		track.Language = "und";
		track.CodecID = "V_VP8";
		track.DefaultDuration = 0;
		track.Video_PixelWidth = 640;
		track.Video_PixelHeight = 360;
		track.CodecPrivate = new byte[0];

		mFW.TrackList.add(track);
		mFW.writeTracks();
	}

    private void openFile() throws IOException {
        File recordFile = new File(recordPath);

        try {
			synchronized(this)
			{
					if (recordFile.exists()) {
						recordFile.delete();
					}

					recordFile.createNewFile();

					fo = new FileOutputStream(recordFile);
					bo = new BufferedOutputStream(fo, BUFFER_SIZE);

					if (recordRtp)
					{
						byte[] buf = new byte[16];
						buf[0] = (byte) 0x52;  // R
						buf[1] = (byte) 0x54;  // T
						buf[2] = (byte) 0x50;  // P

						bo.write(buf, 0, buf.length);
					} else {
						writeAuHeader();
					}
			}
        } catch (IOException e) {
            fo = null;
            bo = null;
            Log.error("can't create buffered output stream for " + recordPath + " " + e.getMessage(), e);
            throw new IOException("can't create buffered output stream for " + recordPath  + " " + e.getMessage());
        }

		Log.info("Recording to " + recordFile.getAbsolutePath() + " recording type is " + (recordRtp ? "RTP" : "Audio")  + " " + pcmu + " " + sampleRate + " " + channels);
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

    private void writeAuHeader() throws IOException {
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

        if (pcmu) {
            auHeader[15] = 1;
        } else {
            auHeader[15] = 3;
        }

        auHeader[16] = (byte)((sampleRate >> 24) & 0xff);
        auHeader[17] = (byte)((sampleRate >> 16) & 0xff);
        auHeader[18] = (byte)((sampleRate >> 8) & 0xff);
        auHeader[19] = (byte)(sampleRate & 0xff);

        auHeader[20] = (byte)((channels >> 24) & 0xff);
        auHeader[21] = (byte)((channels >> 16) & 0xff);
        auHeader[22] = (byte)((channels >> 8) & 0xff);
        auHeader[23] = (byte)(channels & 0xff);

        bo.write(auHeader, 0, auHeader.length);
    }

    public void done() {
		Log.info("Recorder done...");

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

    class DataToWrite
    {
        public byte[] data;
		public int offset;
        public int length;
        public boolean keyframe;
        public long timestamp;

        public DataToWrite(byte[] data, int offset, int length, boolean keyframe, long timestamp)
        {
			/*
			 * We have to copy the data, otherwise caller could
			 * overwrite it.
			 */
			this.data = new byte[length];
			this.offset = offset;
			this.length = length;
			this.keyframe = keyframe;
			this.timestamp = timestamp;

			System.arraycopy(data, offset, this.data, 0, length);
        }
    }

    private long lastWriteTime;

    public void writePacket(byte[] data, int offset, int dataLength, boolean keyframe, long timestamp)
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
	    write(buf, 0, buf.length, keyframe, timestamp);

	} else if (recordWebm) {

	} else {
	    write(data, offset, dataLength, keyframe, timestamp);
	}
    }

    public void write(int[] data, int offset, int length, boolean keyframe, long timestamp) throws IOException
    {
		byte[] byteData = new byte[length * 2];

		for (int i = 0; i < length; i++) {
			byteData[(2 * i)] = (byte) ((data[i + offset] >> 8) & 0xff);
			byteData[(2 * i) + 1] = (byte) (data[i + offset] & 0xff);
		}

        write(byteData, 0, byteData.length, keyframe, timestamp);
    }

    public void write(byte[] data, int offset, int length, boolean keyframe, long timestamp) throws IOException {
        if (done) {
            return;
        }

        synchronized(dataToWrite) {
            dataToWrite.add(new DataToWrite(data, offset, length, keyframe, timestamp));
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
	    	synchronized(this)
	    	{
				if (recordWebm)
				{
					long duration = 0;

					if (d.keyframe || lastTimecode == 0)
					{
						if (lastTimecode != 0)
						{
							//Log.info("writeData end cluster " + d.data);
							duration = d.timestamp - lastTimecode;
							mFW.endCluster();
						}
						//Log.info("writeData start cluster " + d.timestamp);
						mFW.startCluster(d.timestamp);
					}

					lastTimecode = d.timestamp;

					MatroskaFileFrame frame = new MatroskaFileFrame();
					frame.TrackNo = 1;
					frame.Duration = duration;
					frame.Timecode = d.timestamp;
					frame.Reference = 0;
					frame.KeyFrame = d.keyframe;
					frame.Data = d.data;
					mFW.addFrame(frame);

					//Log.info("writeData video " + d.data);

				} else {
                	bo.write(d.data, 0, d.length);
					dataSize += d.length;
				}
            }
        } catch (IOException e) {
            Log.error("Can't record to " + recordPath, e);
	    	done();
        }
    }

    private void writeDataSize() {
        try {
			synchronized(this)
			{
				if (recordWebm)
				{
					mFW.endCluster();
					iFW.close();

				} else {
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
							Log.error("Unable to write data size to recording " + recordPath + " " + e.getMessage(), e);
						}
					}
				}
	    	}

        } catch (IOException e) {
	    	Log.error("Exception closing recording " + recordPath + " " + e.getMessage(), e);
        }
    }

    public static void setDefaultRecordingDirectory(
	    String defaultRecordDirectory) throws ParseException {

	checkPermission(defaultRecordDirectory, true);

        Recorder.defaultRecordDirectory = defaultRecordDirectory;

        Log.info("Default recording directory set to "
                + defaultRecordDirectory);
    }

    public static String getRecordingDirectory() {
        return defaultRecordDirectory;
    }

    public void writeWebPImage(byte[] data, int offset, int length, long timestamp)
    {
		try {
			Log.info("writeWebPImage " + length + " " + offset);
			String outputFilename = recordPath.replace(".webm", timestamp + ".webp");
			FileOutputStream oFS = new FileOutputStream(outputFilename);

			oFS.write("RIFF".getBytes());
			writeIntLE(oFS, length+12);

			oFS.write("WEBPVP8".getBytes());
			oFS.write(0x20);

			writeIntLE(oFS, length);
			oFS.write(data);
			oFS.close();

		} catch (Exception e) {
			Log.error("writeWebPImage", e);
		}
	}

	private void writeIntLE(FileOutputStream out, int value) {

		try {
			out.write(value & 0xFF);
			out.write((value >> 8) & 0xFF);
			out.write((value >> 16) & 0xFF);
			out.write((value >> 24) & 0xFF);

		} catch (Exception e) {
			Log.error("writeIntLE", e);
		}
	}

}
