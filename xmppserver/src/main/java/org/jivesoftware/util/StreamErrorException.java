/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.xmpp.packet.StreamError;

/**
 * An exception that wraps a {@link StreamError}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class StreamErrorException extends Exception
{
    private final StreamError streamError;

    public StreamErrorException(StreamError.Condition condition)
    {
        this.streamError = new StreamError(condition);
    }

    public StreamErrorException(StreamError.Condition condition, String message)
    {
        super(message);
        this.streamError = new StreamError(condition);
    }

    public StreamErrorException(StreamError.Condition condition, String message, Throwable cause)
    {
        super(message, cause);
        this.streamError = new StreamError(condition);
    }

    public StreamErrorException(StreamError.Condition condition, Throwable cause)
    {
        super(condition.name(), cause);
        this.streamError = new StreamError(condition);
    }

    public StreamErrorException(StreamError.Condition condition, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
    {
        super(message, cause, enableSuppression, writableStackTrace);
        this.streamError = new StreamError(condition);
    }

    public StreamErrorException(StreamError streamError)
    {
        super(streamError != null && streamError.getText() != null && !streamError.getText().isEmpty() ? streamError.getText() : null);
        this.streamError = streamError;
    }

    public StreamErrorException(StreamError streamError, String message)
    {
        super(message);
        this.streamError = streamError;
    }

    public StreamErrorException(StreamError streamError, String message, Throwable cause)
    {
        super(message, cause);
        this.streamError = streamError;
    }

    public StreamErrorException(StreamError streamError, Throwable cause)
    {
        super(streamError != null && streamError.getText() != null && !streamError.getText().isEmpty() ? streamError.getText() : null, cause);
        this.streamError = streamError;
    }

    public StreamErrorException(StreamError streamError, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
    {
        super(message, cause, enableSuppression, writableStackTrace);
        this.streamError = streamError;
    }

    public StreamError getStreamError() {
        return streamError;
    }
}
