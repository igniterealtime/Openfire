package org.jivesoftware.openfire.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import org.apache.mina.common.IoFilter;

/**
 * A new Stream Compression algorithm (as defined by XEP-0138) can be added to
 * Openfire by implementing the StreamCompressor interface. All implementing
 * classes should be registered to the StreamCompressionManager.
 * 
 * Each class implementing this interface will return three objects primarily
 * used for compression: on one side, there are a Writer and Reader object
 * (returned by {@link #getWriter(OutputStream)} and
 * {@link #getReader(InputStream)}, and on the other side is a
 * CompressionFilter object (returned by {@link #getCompressingIOFilter()}. The
 * former is used by the (old) blocking IO package, while the latter is used by
 * the (newer) non-Blocking network IO package (Apache MINA).
 * 
 * @author Guus der Kinderen, guus@nimbuzz.com
 * @see StreamCompressionManager
 */
public interface StreamCompressor
{
	/**
	 * The identifier used by the XMPP server to denote this compression
	 * algorithm (e.g. 'zlib' or 'lzw'). This method cannot return null or an
	 * empty String.
	 * 
	 * The return value of this method is used in the compression feature
	 * negotiation, as described in XEP-0138.
	 * 
	 * @return The identifier of the compression algorithm used.
	 */
	public String getMethodIdentifier();

	/**
	 * This method is used to apply compression to an OutputStream object.
	 * 
	 * @param outputStream
	 *            The (uncompressed) OutputStream
	 * @return Writer object that writes compressed data.
	 * @throws IOException
	 */
	public Writer getWriter(final OutputStream outputStream) throws IOException;

	/**
	 * This method is used to apply decompression to an InputStream object.
	 * 
	 * @param inputStream
	 *            The (compressed) OutputStream
	 * @return Reader object that reads uncompressed data.
	 * @throws IOException
	 */
	public Reader getReader(final InputStream inputStream) throws IOException;

	/**
	 * Returns a MINA filter that applies the compression algorithm.
	 * 
	 * @return MINA filter that applies compression.
	 */
	public IoFilter getCompressingIOFilter();
}
