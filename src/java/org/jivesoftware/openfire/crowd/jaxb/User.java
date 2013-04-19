/*
 * Copyright (C) 2012 Issa Gorissen <issa-gorissen@usa.net>. All rights reserved.
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
package org.jivesoftware.openfire.crowd.jaxb;

import java.util.Date;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

public class User {
	@XmlAttribute
	public String name;
	
	@XmlElement(name="display-name")
	public String displayName;
	
	@XmlElement(name="first-name")
	public String firstName;
	
	@XmlElement(name="last-name")
	public String lastName;
	
	public String email;
	
	private org.jivesoftware.openfire.user.User openfireUser;
	
	public synchronized org.jivesoftware.openfire.user.User getOpenfireUser() {
		if (openfireUser == null) {
			openfireUser = new org.jivesoftware.openfire.user.User(name, displayName, email, new Date(), new Date());
		}
		return openfireUser;
	}
}
