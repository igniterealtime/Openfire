package com.reucon.openfire.plugin.archive;

import com.reucon.openfire.plugin.archive.model.Conversation;

import java.util.Collection;
import java.util.Date;

/**
 * Maintains an index for message retrieval.
 */
public interface IndexManager
{
    /**
     * Asynchronously indexes the given object.
     * @param object the object to index.
     * @return <code>true</code> if successfully queued for indexing, <code>false</code> otherwise.
     */
    boolean indexObject(Object object);

    /**
     * Rebuilds the index.
     *
     * @return the number of messages indexed or -1 on error.
     */
    int rebuildIndex();

    Collection<String> searchParticipant(String token);

    Collection<Conversation> findConversations(String[] participants, Date startDate, Date endDate, String keywords);

    void destroy();
}
