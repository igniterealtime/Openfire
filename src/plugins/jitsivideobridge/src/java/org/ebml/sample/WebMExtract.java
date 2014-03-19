package org.ebml.sample;

import java.io.FileOutputStream;
import java.io.IOException;

import org.ebml.io.FileDataSource;
import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;

/**
 * <p>Title: JEBML</p>
 * <p>Description: Java Classes to Extract keyframes from WebM Files as WebP Images</p>
 * <p>Copyright: Copyright (c) 2011 Brooss</p>
 * <p>Company: </p>
 * @author brooss
 * @version 1.0
 */

public class WebMExtract {
	public static void main(String[] args) {
		System.out.println("JEBML WebMExtract");

	if (args.length < 1) {
      System.out.println("Please provide a WebM filename on the command-line");
      return;
    }
		try {
			readFile(args[0]);

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	static void readFile(String filename) throws IOException
	{
		System.out.println("Scanning file: " + filename);
		long startTime = System.currentTimeMillis();

		FileDataSource iFS = new FileDataSource(filename);
		MatroskaFile mF = new MatroskaFile(iFS);
		mF.setScanFirstCluster(true);
		mF.readFile();

		System.out.println(mF.getReport());

		MatroskaFileTrack track=null;
		for(MatroskaFileTrack t : mF.getTrackList() ) {
			if(t.CodecID.compareTo("V_VP8")==0)
				track = t;
		}
		
		if (track!=null)
		{
			MatroskaFileFrame frame = mF.getNextFrame(track.TrackNo);
			int count=0;
			while (frame != null)
			{
				if(frame.isKeyFrame()) {
					System.out.println("Extracting VP8 frame "+count);
					String outputFilename = filename + ""+count+".webp";// + ".wav";
					FileOutputStream oFS = new FileOutputStream(outputFilename);
	
					oFS.write("RIFF".getBytes());
	
					writeIntLE(oFS, frame.Data.length+20-8);
	
					oFS.write("WEBPVP8".getBytes());
					oFS.write(0x20);
					writeIntLE(oFS, (frame.Data.length+20-8)-0xc);
	
					oFS.write(frame.Data);
				}
				frame = mF.getNextFrame(track.TrackNo);
				count++;
			}
		}

		long endTime = System.currentTimeMillis();
		System.out.println("Scan complete. Took: " + ((endTime - startTime) / 1000.0) + " seconds");
	}

	public static void writeIntLE(FileOutputStream out, int value) {

		try {
			out.write(value & 0xFF);
			out.write((value >> 8) & 0xFF);
			out.write((value >> 16) & 0xFF);
			out.write((value >> 24) & 0xFF);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
