/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/**
 * TLSStreamHandler is responsible for securing plain connections by negotiating TLS. By creating
 * a new instance of this class the plain connection will be secured.
 *
 * @author Hao Chen
 */
public class TLSStreamHandler {

	private TLSStreamWriter writer;

	private TLSStreamReader reader;

	private TLSWrapper wrapper;

	private ReadableByteChannel rbc;
	private WritableByteChannel wbc;

	private SSLEngine tlsEngine;

	/*
	 * During the initial handshake, keep track of the next SSLEngine operation that needs to occur:
	 * 
	 * NEED_WRAP/NEED_UNWRAP
	 * 
	 * Once the initial handshake has completed, we can short circuit handshake checks with
	 * initialHSComplete.
	 */
	private HandshakeStatus initialHSStatus;
	private boolean initialHSComplete;

	private int appBBSize;
	private int netBBSize;

	/*
	 * All I/O goes through these buffers. It might be nice to use a cache of ByteBuffers so we're
	 * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine. Outbound application data is
	 * supplied to us by our callers.
	 */
	private ByteBuffer incomingNetBB;
	private ByteBuffer outgoingNetBB;

	private ByteBuffer appBB;

	/*
	 * An empty ByteBuffer for use when one isn't available, say as a source buffer during initial
	 * handshake wraps or for close operations.
	 */
	private static ByteBuffer hsBB = ByteBuffer.allocate(0);

    /**
     * Creates a new TLSStreamHandler and secures the plain socket connection.
     *
     * @param clientMode boolean indicating if this entity is a client or a server.
     * @param socket the plain socket connection to secure
     * @throws IOException
     */
    public TLSStreamHandler(Socket socket, boolean clientMode) throws IOException {
		wrapper = new TLSWrapper(clientMode);
        tlsEngine = wrapper.getTlsEngine();
		reader = new TLSStreamReader(wrapper, socket);
		writer = new TLSStreamWriter(wrapper, socket);

		rbc = Channels.newChannel(socket.getInputStream());
		wbc = Channels.newChannel(socket.getOutputStream());
		initialHSStatus = HandshakeStatus.NEED_UNWRAP;
		initialHSComplete = false;

		netBBSize = tlsEngine.getSession().getPacketBufferSize();
		appBBSize = tlsEngine.getSession().getApplicationBufferSize();

		incomingNetBB = ByteBuffer.allocate(netBBSize);
		outgoingNetBB = ByteBuffer.allocate(netBBSize);
		outgoingNetBB.position(0);
		outgoingNetBB.limit(0);

		appBB = ByteBuffer.allocate(appBBSize);

        if (clientMode) {
            socket.setSoTimeout(0);
            socket.setKeepAlive(true);
            initialHSStatus = HandshakeStatus.NEED_WRAP;
            tlsEngine.beginHandshake();
        }

        while (!initialHSComplete) {
			initialHSComplete = doHandshake(null);
		}
	}

	public InputStream getInputStream(){
		return reader.getInputStream();
	}

	public OutputStream getOutputStream(){
		return writer.getOutputStream();
	}

	private boolean doHandshake(SelectionKey sk) throws IOException {

		SSLEngineResult result;

		if (initialHSComplete) {
			return initialHSComplete;
		}

		/*
		 * Flush out the outgoing buffer, if there's anything left in it.
		 */
		if (outgoingNetBB.hasRemaining()) {

			if (!flush(outgoingNetBB)) {
				return false;
			}

			// See if we need to switch from write to read mode.

			switch (initialHSStatus) {

			/*
			 * Is this the last buffer?
			 */
			case FINISHED:
				initialHSComplete = true;

			case NEED_UNWRAP:
				if (sk != null) {
					sk.interestOps(SelectionKey.OP_READ);
				}
				break;
			}

			return initialHSComplete;
		}

		switch (initialHSStatus) {

		case NEED_UNWRAP:
			if (rbc.read(incomingNetBB) == -1) {
				tlsEngine.closeInbound();
				return initialHSComplete;
			}

			needIO: while (initialHSStatus == HandshakeStatus.NEED_UNWRAP) {
				/*
				 * Don't need to resize requestBB, since no app data should be generated here.
				 */
				incomingNetBB.flip();
				result = tlsEngine.unwrap(incomingNetBB, appBB);
				incomingNetBB.compact();

				initialHSStatus = result.getHandshakeStatus();

				switch (result.getStatus()) {

				case OK:
					switch (initialHSStatus) {
					case NOT_HANDSHAKING:
						throw new IOException("Not handshaking during initial handshake");

					case NEED_TASK:
						initialHSStatus = doTasks();
						break;

					case FINISHED:
						initialHSComplete = true;
						break needIO;
					}

					break;

				case BUFFER_UNDERFLOW:
					/*
					 * Need to go reread the Channel for more data.
					 */
					if (sk != null) {
						sk.interestOps(SelectionKey.OP_READ);
					}
					break needIO;

				default: // BUFFER_OVERFLOW/CLOSED:
					throw new IOException("Received" + result.getStatus()
							+ "during initial handshaking");
				}
			}

			/*
			 * Just transitioned from read to write.
			 */
			if (initialHSStatus != HandshakeStatus.NEED_WRAP) {
				break;
			}

		// Fall through and fill the write buffers.

		case NEED_WRAP:
			/*
			 * The flush above guarantees the out buffer to be empty
			 */
			outgoingNetBB.clear();
			result = tlsEngine.wrap(hsBB, outgoingNetBB);
			outgoingNetBB.flip();

			initialHSStatus = result.getHandshakeStatus();

			switch (result.getStatus()) {
			case OK:

				if (initialHSStatus == HandshakeStatus.NEED_TASK) {
					initialHSStatus = doTasks();
				}

				if (sk != null) {
					sk.interestOps(SelectionKey.OP_WRITE);
				}

				break;

			default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
				throw new IOException("Received" + result.getStatus()
						+ "during initial handshaking");
			}
			break;

		default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
			throw new RuntimeException("Invalid Handshaking State" + initialHSStatus);
		} // switch

		return initialHSComplete;
	}

	/*
	 * Writes ByteBuffer to the SocketChannel. Returns true when the ByteBuffer has no remaining
	 * data.
	 */
	private boolean flush(ByteBuffer bb) throws IOException {
		wbc.write(bb);
		return !bb.hasRemaining();
	}

	/*
	 * Do all the outstanding handshake tasks in the current Thread.
	 */
	private SSLEngineResult.HandshakeStatus doTasks() {

		Runnable runnable;

		/*
		 * We could run this in a separate thread, but do in the current for now.
		 */
		while ((runnable = tlsEngine.getDelegatedTask()) != null) {
			runnable.run();
		}
		return tlsEngine.getHandshakeStatus();
	}

}
