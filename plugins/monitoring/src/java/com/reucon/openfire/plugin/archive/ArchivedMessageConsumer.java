package com.reucon.openfire.plugin.archive;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;

/**
 * Consumes an ArchivedMessage.
 */
public interface ArchivedMessageConsumer
{
    boolean consume(ArchivedMessage message);
}
