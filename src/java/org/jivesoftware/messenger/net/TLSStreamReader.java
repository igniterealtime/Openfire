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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.jivesoftware.util.Log;

/**
 * A <code>TLSStreamReader</code> that returns a special InputStream that hides the ByteBuffers
 * used by the underlying Channels.
 * 
 * @author Hao Chen
 */
public class TLSStreamReader {

	/**
	 * <code>TLSWrapper</code> is a TLS wrapper for connections requiring TLS protocol.
	 */
	private TLSWrapper wrapper;

	private ReadableByteChannel rbc;

	/**
	 * <code>inNetBB</code> buffer keeps data read from socket.
	 */
	private ByteBuffer inNetBB;

	/**
	 * <code>inAppBB</code> buffer keeps decypted data.
	 */
	private ByteBuffer inAppBB;

	public TLSStreamReader(TLSWrapper tlsWrapper, Socket socket) throws IOException {
		wrapper = tlsWrapper;
		rbc = Channels.newChannel(socket.getInputStream());
		inNetBB = ByteBuffer.allocate(wrapper.getNetBuffSize());
		inAppBB = ByteBuffer.allocate(wrapper.getAppBuffSize());
	}

	/*
	 * Read TLS encrpyted data from SocketChannel, and use <code>decrypt</code> method to decypt.
	 */
	private void doRead() throws IOException {
		inNetBB.clear();
		inAppBB.clear();
		final int cnt = rbc.read(inNetBB);
		if (cnt > 0) {
			ByteBuffer tlsInput = inNetBB;

			inAppBB = decrypt(tlsInput, inAppBB);
			inAppBB.flip();
		} else {
			if (cnt == -1) {
				rbc.close();
			}
		}
	}

	/*
	 * This method uses <code>TLSWrapper</code> to decrypt TLS encrypted data.
	 */
	private ByteBuffer decrypt(final ByteBuffer input, final ByteBuffer output) throws IOException {
		TLSStatus stat = null;
		ByteBuffer out = output;
		do {
			input.flip();

			out = wrapper.unwrap(input, out);

            if (input.hasRemaining()) {
				input.compact();
			}

			stat = wrapper.getStatus();
		} while ((stat == TLSStatus.NEED_READ || stat == TLSStatus.OK) && input.hasRemaining());

		if (input.hasRemaining()) {
			input.rewind();
		} else {
			input.clear();
		}

		return out;
	}

	public InputStream getInputStream() {
		return createInputStream();
	}

	/*
	 * Returns an input stream for a ByteBuffer. The read() methods use the relative ByteBuffer
	 * get() methods.
	 */
	private InputStream createInputStream() {
		return new InputStream() {
			public synchronized int read() throws IOException {
				doRead();
				if (!inAppBB.hasRemaining()) {
					return -1;
				}
				return inAppBB.get();
			}

			public synchronized int read(byte[] bytes, int off, int len) throws IOException {
                doRead();
				len = Math.min(len, inAppBB.remaining());
				inAppBB.get(bytes, off, len);
				return len;
			}
		};
	}
}
