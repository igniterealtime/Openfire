package org.jivesoftware.openfire.net;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.mina.filter.CompressionFilter;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import com.jcraft.jzlib.ZOutputStream;

/**
 * Implements ZLib Stream Compression.
 * 
 * Note that much of this code was copy/pasted from various other Openfire
 * classes. These classes always assumed the ZLIB algorithm, if compression was
 * enabled.
 * 
 * @author Guus der Kinderen, guus@nimbuzz.com
 */
public class ZLibStreamCompressor implements StreamCompressor
{
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.net.StreamCompressor#getMethodIdentifier()
	 */
	public String getMethodIdentifier()
	{
		return "zlib";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.net.StreamCompressor#getWriter(java.io.OutputStream)
	 */
	public Writer getWriter(OutputStream outputStream)
			throws UnsupportedEncodingException
	{
		// TODO: move traffic counting outside this class!
		final ZOutputStream out = new ZOutputStream(
			ServerTrafficCounter.wrapOutputStream(outputStream),
			JZlib.Z_BEST_COMPRESSION);
		out.setFlushMode(JZlib.Z_PARTIAL_FLUSH);

		return new BufferedWriter(new OutputStreamWriter(out,
			SocketConnection.CHARSET));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.net.StreamCompressor#getReader(java.io.InputStream)
	 */
	public Reader getReader(InputStream inputStream)
			throws UnsupportedEncodingException
	{
		// TODO: move traffic counting outside this class!
		final ZInputStream in = new ZInputStream(
			ServerTrafficCounter.wrapInputStream(inputStream));
		in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
		return new InputStreamReader(in, SocketConnection.CHARSET);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.openfire.net.StreamCompressor#getCompressingIOFilter()
	 */
	public CompressionFilter getCompressingIOFilter()
	{
		return new CompressionFilter(CompressionFilter.COMPRESSION_MAX);
	}
}
