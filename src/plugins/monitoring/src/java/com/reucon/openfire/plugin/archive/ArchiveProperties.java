package com.reucon.openfire.plugin.archive;

/**
 * Literals for configuration properties. 
 */
public interface ArchiveProperties
{
	// TODO: change the below to a separate property to allow archiving but disable/enable XEP-0136
    String ENABLED = "conversation.metadataArchiving";
    String INDEX_DIR = "archive.indexdir";
    // Unnecessary since Open Archive Archive Manager no longer archives messages
    String CONVERSATION_TIMEOUT = "conversation.idleTime";
}
