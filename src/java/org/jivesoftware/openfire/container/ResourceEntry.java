/**
 * $RCSfile$
 * $Revision: 2993 $
 * $Date: 2005-10-24 18:11:33 -0300 (Mon, 24 Oct 2005) $
 *
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

package org.jivesoftware.openfire.container;

import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

/**
 * Resource entry.
 *
 * @author wmz7year
 */
public class ResourceEntry {


    /**
     * The "last modified" time of the origin file at the time this class
     * was loaded, in milliseconds since the epoch.
     */
    public long lastModified = -1;


    /**
     * Binary content of the resource.
     */
    public byte[] binaryContent = null;


    /**
     * Loaded class.
     */
    public volatile Class<?> loadedClass = null;


    /**
     * URL source from where the object was loaded.
     */
    public URL source = null;


    /**
     * URL of the codebase from where the object was loaded.
     */
    public URL codeBase = null;


    /**
     * Manifest (if the resource was loaded from a JAR).
     */
    public Manifest manifest = null;


    /**
     * Certificates (if the resource was loaded from a JAR).
     */
    public Certificate[] certificates = null;


}

