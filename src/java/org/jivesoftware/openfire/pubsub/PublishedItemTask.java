/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;

import java.util.Queue;
import java.util.TimerTask;

/**
 * A timed maintenance task that updates the database by adding and/or
 * removing <code>PublishedItem</code>s in regular intervals.
 * 
 * @author Matt Tucker
 */
public class PublishedItemTask extends TimerTask {
    /**
     * Queue that holds the items that need to be added to the database.
     */
    private Queue<PublishedItem> itemsToAdd = null;

    /**
     * Queue that holds the items that need to be deleted from the database.
     */
    private Queue<PublishedItem> itemsToDelete = null;

    /**
     * The service to perform the published item tasks on.
     */
    private PubSubService service = null;

    /**
     * The number of items to save on each run of the maintenance process.
     */
    private int items_batch_size = 50;

    public PublishedItemTask(PubSubService service) {
        this.service = service;
        this.itemsToAdd = service.getItemsToAdd();
        this.itemsToDelete = service.getItemsToDelete();
    }

    public void run() {
        try {
            PublishedItem entry;
            boolean success;
            // Delete from the database items contained in the itemsToDelete queue
            for (int index = 0; index <= items_batch_size && !itemsToDelete.isEmpty(); index++) {
                entry = itemsToDelete.poll();
                if (entry != null) {
                    success = PubSubPersistenceManager.removePublishedItem(service, entry);
                    if (!success) {
                        itemsToDelete.add(entry);
                    }
                }
            }
            // Save to the database items contained in the itemsToAdd queue
            for (int index = 0; index <= items_batch_size && !itemsToAdd.isEmpty(); index++) {
                entry = itemsToAdd.poll();
                if (entry != null) {
                    success = PubSubPersistenceManager.createPublishedItem(service, entry);
                    if (!success) {
                        itemsToAdd.add(entry);
                    }
                }
            }
        } catch (Throwable e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }
}
