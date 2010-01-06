/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.pep;

import org.jivesoftware.openfire.pubsub.PublishedItemTask;

/**
 * TimerTask that unloads services from memory, after they have been expired
 * from cache.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PublishedPEPServiceTask extends PublishedItemTask {

	private final PEPServiceManager manager;

	public PublishedPEPServiceTask(PEPService service, PEPServiceManager manager) {
		super(service);
		this.manager = manager;
	}

	@Override
	public void run() {
		// Somewhat of a hack to unload the PEPService after it has been removed
		// from the cache. New scheduled packets will re-instate the service.
		PEPService service = (PEPService) this.getService();
		if (manager.hasCachedService(service.getAddress())) {
			super.run();
		} else {
			manager.unload(service);
		}
	}
}
