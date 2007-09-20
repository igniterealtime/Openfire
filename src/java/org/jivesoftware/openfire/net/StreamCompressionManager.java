package org.jivesoftware.openfire.net;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

/**
 * The StreamCompressionManager allows for run-time adjustable, pluggable stream
 * compression management. This means that stream compression algorithms can be
 * added or removed run-time.
 * 
 * @see StreamCompressor
 * @author Guus der Kinderen, guus@nimbuzz.com
 */
public class StreamCompressionManager
{
	/**
	 * The collection of all available Stream Compressors.
	 */
	private static final Map<String, StreamCompressor> availableCompressors = new ConcurrentHashMap<String, StreamCompressor>();

	/**
	 * The singleton object of this class
	 */
	private static final StreamCompressionManager instance = new StreamCompressionManager();

	// TODO: To make the interface completely pluggable, this needs to be done
	// runtime instead.
	static
	{
		register(new ZLibStreamCompressor());
	}

	/**
	 * The constructor of this class has been made private to enforce singleton
	 * behavior. The singleton instance of this class can be retrieved by
	 * calling {@link #getInstance()}.
	 * 
	 * @see #getInstance()
	 */
	private StreamCompressionManager()
	{
	// Isn't intended to do anything.
	}

	/**
	 * Returns the singleton instance of this class.
	 * 
	 * @return The StreamCompressorManager instance.
	 */
	public StreamCompressionManager getInstance()
	{
		return instance;
	}

	/**
	 * Registers a new StreamCompressor with this Manager. The compression
	 * algorithm provided by the StreamCompressor will be made available for use
	 * in Openfire.
	 * 
	 * If, prior to the invocation of this method, this manager contains a
	 * StreamCompressor that matches the method identifier of the compressor to
	 * be added, the old StreamCompressor instance will be overwritten.
	 * 
	 * Note that this method cannot return null or an empty String.
	 * 
	 * @param compressor
	 *            The StreamCompressor to be added.
	 * @return The method identifier of the newly added StreamCompressor.
	 * @throws IllegalArgumentException
	 *             If the compressor argument is null, or if the compressor
	 *             argument is a StreamCompressor instance of which the
	 *             getIdentifier method returns null.
	 */
	public static String register(final StreamCompressor compressor)
	{
		if (compressor == null)
		{
			throw new IllegalArgumentException(
				"Parameter 'compressor' cannot be null.");
		}

		final String identifier = compressor.getMethodIdentifier();
		if (identifier == null || identifier.length() == 0)
		{
			throw new IllegalArgumentException(
				"Parameter 'compressor' must be a StreamCompressor instance of which the method #getIdentifier does not return null or an empty String.");
		}

		availableCompressors.put(identifier, compressor);
		return identifier;
	}

	/**
	 * Deregisters the StreamCompressor that matches the provided identifier.
	 * This removes the StreamCompressor from the set of available
	 * StreamCompression algorithms broadcasted by Openfire.
	 * 
	 * @param identifier
	 *            The method identifier of the StreamCompressor to be removed.
	 * @throws IllegalArgumentException
	 *             If provided 'identifier' argument is null.
	 * @throws IllegalStateException
	 *             If no StreamCompressor matching the method identifier has
	 *             been registered.
	 */
	public static void deregister(final String identifier)
	{
		if (identifier == null)
		{
			throw new IllegalArgumentException(
				"Parameter 'identifier' cannot be null.");
		}

		if (availableCompressors.remove(identifier) == null)
		{
			throw new IllegalStateException(
				"Cannot deregister compressor. No compressor identified by '"
						+ identifier + "' has been registered.");
		}
	}

	/**
	 * Checks if a StreamCompressor matching the provided identifer was
	 * registered with this Manager.
	 * 
	 * @param methodIdentifier
	 *            The identifier to check.
	 * @return ''true'' the provided compression method identifier is available
	 *         for usage, ''false'' otherwise.
	 */
	public static boolean isAvailable(final String methodIdentifier)
	{
		return availableCompressors.containsKey(methodIdentifier);
	}

	/**
	 * Lists all available compressors, by returning an (unmodifiable) Set of
	 * all compression method identifiers. This method will return an empty Set
	 * if no StreamCompressors are available.
	 * 
	 * @return A set of all compression method idenfiers.
	 */
	public static Set<String> allAvailableMethods()
	{
		return Collections.unmodifiableSet(availableCompressors.keySet());
	}

	/**
	 * Returns the StreamCompressor matching the provided method identifier.
	 * Note that this method will not return ''null'' values. If unavailable
	 * StreamCompressors are requested, RuntimeExceptions will be thrown.
	 * 
	 * @param methodIdentifier
	 *            The identifier that denotes the requested StreamCompressor
	 *            instance.
	 * @return StreamCompressor matching the identifier.
	 * 
	 * @throws IllegalArgumentException
	 *             If provided 'methodIdentifier' argument is null.
	 * @throws IllegalStateException
	 *             If no StreamCompressor matching the method identifier has
	 *             been registered.
	 * 
	 */
	public static StreamCompressor getCompressor(String methodIdentifier)
	{
		if (methodIdentifier == null)
		{
			throw new IllegalArgumentException(
				"Argument 'methodIdentifier' cannot be null.");
		}

		final StreamCompressor compressor = availableCompressors.get(methodIdentifier);
		if (compressor == null)
		{
			throw new IllegalStateException("No compressor identified by '"
					+ methodIdentifier + "' has been registered.");
		}

		return compressor;
	}

	/**
	 * Creates and returns a 'compression' element suitable to be included as a
	 * child element of the stream element. The compression element will reflect
	 * all compression methods available from the manager.
	 * 
	 * @return A compression XML element containing all compression methods, or
	 *         'null' if no compression methods are available.
	 */
	public static Element getStreamCompressionFeature()
	{
		// TODO: we shouldn't iterate over the entire Map all the time. We can
		// cache the response, and change the cache if the map gets changed
		// instead.
		if (!availableCompressors.isEmpty())
		{
			final Element compression = DocumentHelper.createElement(new QName(
				"compression", new Namespace("",
					"http://jabber.org/features/compress")));
			for (String method : availableCompressors.keySet())
			{
				compression.addElement("method").setText(method);
			}
			return compression;
		}
		return null;
	}
}
