/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.filetransfer;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Thrown by a {@link FileTransferEventListener} when a file transfer is rejected by the Interceptor. The file
 * transfer is aborted and the participating parties are notified.
 *
 * @author Alexander Wenckus
 */
public class FileTransferRejectedException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    /**
     * Text to include in a message that will be sent to the sender of the packet that got
     * rejected. If no text is specified then no message will be sent to the user.
     */
    private String rejectionMessage;

    public FileTransferRejectedException() {
        super();
    }

    public FileTransferRejectedException(String msg) {
        super(msg);
    }

    public FileTransferRejectedException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public FileTransferRejectedException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    @Override
    public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    @Override
    public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }

    /**
     * Returns the text to include in a message that will be sent to the intitiator and target
     * of the file transfer that got rejected or {@code null} if none was defined. If no text was
     * specified then no message will be sent to the parties of the rejected file transfer.
     *
     * @return the text to include in a message that will be sent to the parties of the file
     * transfer that got rejected or {@code null} if none was defined.
     */
    public String getRejectionMessage() {
        return rejectionMessage;
    }

    /**
     * Sets the text to include in a message that will be sent to the intiator and target of the
     * file transfer that got rejected or {@code null} if no message will be sent to the parties
     * of the rejected file transfer. Bt default, no message will be sent.
     *
     * @param rejectionMessage the text to include in the notification message for the rejection.
     */
    public void setRejectionMessage(String rejectionMessage) {
        this.rejectionMessage = rejectionMessage;
    }
}
