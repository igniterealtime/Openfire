/* OF-2061: Update the default configuration for leaf nodes of PEP services (those with a serviceID that contain the '@' symbol) to ensure that items are persisted.
   provided that the maxPayloadSize still is at what Openfire used by default up to this point (5120).
   OF-2062: Increase the maxPayloadSize in the default configuration for leaf nodes of PEP services (those with a serviceID that contain the '@' symbol,
   which indicates that the serviceID matches a JID), provided that the maxPayloadSize still is at what Openfire used by default up to this point (5120). */
UPDATE ofPubsubDefaultConf SET persistItems = 1, maxPayloadSize = 10485760 WHERE serviceID LIKE '%@%' AND leaf = 1 AND maxPayloadSize = 5120;

/* OF-2061 & OF-262: Apply the same configuration change to all existing nodes that seem to use the default configuration. */
UPDATE ofPubsubNode SET persistItems = 1, maxPayloadSize = 10485760 WHERE serviceID LIKE '%@%' AND leaf = 1 AND maxPayloadSize = 5120;

UPDATE ofVersion SET version = 31 WHERE name = 'openfire'
