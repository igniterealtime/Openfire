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
package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.RequestFilter;
import org.jivesoftware.xmpp.workgroup.RequestFilterFactory;

public class BasicRequestFilterFactory extends RequestFilterFactory {
    RequestFilter filter = new BasicRequestFilter();

    @Override
	public RequestFilter getFilter() {
        return filter;
    }
}
