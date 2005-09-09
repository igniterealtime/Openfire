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

    private TLSStatus lastStatus;

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
		//inNetBB.clear();
        System.out.println("doRead inNet position: " + inNetBB.position());
        /*if (lastStatus != TLSStatus.UNDERFLOW) {
		    inAppBB.clear();
        }*/
        final int cnt = rbc.read(inNetBB);
		if (cnt > 0) {
            System.out.println("doRead inNet position: " + inNetBB.position() + " capacity: " + inNetBB.capacity() + " (after read)");
            System.out.println("doRead inAppBB (before decrypt) position: " + inAppBB.position() + " limit: " + inAppBB.limit() + " capacity: " + inAppBB.capacity());
			inAppBB = decrypt(inNetBB, inAppBB);
            System.out.println("doRead inAppBB (after decrypt) position: " + inAppBB.position() + " limit: " + inAppBB.limit() + " capacity: " + inAppBB.capacity() + " lastStatus: " + lastStatus);
            if (lastStatus == TLSStatus.OK) {
                inAppBB.flip();
            }
            else {
                System.out.println("Intento de nuevo doRead");
                doRead();
            }
        } else {
			if (cnt == -1) {
                System.out.println("????: No habia NADA");
				rbc.close();
			}
            else {
                System.out.println("????: Y esto??? " + cnt);
            }
        }
	}

	/*
	 * This method uses <code>TLSWrapper</code> to decrypt TLS encrypted data.
	 */
	private ByteBuffer decrypt(ByteBuffer input, ByteBuffer output) throws IOException {
		TLSStatus stat = null;
		ByteBuffer out = output;
        //int i = 1;
        input.flip();
        do {

			out = wrapper.unwrap(input, out);

            /*if (input.hasRemaining()) {
				input.compact();
			}*/

			stat = wrapper.getStatus();
            //if (i > 1) {
                //System.out.println(i + " - " + stat + " - " + input.hasRemaining() + " - pos: " + input.position() + " - lim: " + input.limit());
            //}
            //i++;
        } while ((stat == TLSStatus.NEED_READ || stat == TLSStatus.OK) && input.hasRemaining());

        if (stat != TLSStatus.OK) {
            System.out.println(stat);
        }

        if (input.hasRemaining()) {
            System.out.println("hasRemaining = true " + stat);
            System.out.println("Hice rewind");
			input.rewind();
		} else {
			input.clear();
            System.out.println("inNet position con clear: " + inNetBB.position() + " / " + input.position());
		}

        lastStatus = stat;

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
                int b = inAppBB.remaining();
                int len2 = Math.min(len, b);
                if (len2 == 0) {
                    System.out.println("????: -1 -1 -1 -1");
                     return -1;
                }
				inAppBB.get(bytes, off, len2);
                System.out.println("#createInputStream. available in buffer : " + b + " requested: " + len2);
                if (inAppBB.hasRemaining()) {
                    inAppBB.compact();
                    System.out.println("#createInputStream. inAppBB compact position: " + inAppBB.position() + " limit: " + inAppBB.limit());
                }
                else {
                    inAppBB.clear();
                }
                //inAppBB.compact();
                if (len2 <= 0) {
                    System.out.println("????: " + new String(inAppBB.array()));
                }
                return len2;
			}
		};
	}
}
