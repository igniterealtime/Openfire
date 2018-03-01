/*
 * Copyright 2016 Anno van Vliet
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
package org.jivesoftware.openfire.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;

/**
 * An interface to an In and Exporter.
 *
 * @author Anno van Vliet
 *
 */
public interface InExporter {

  /**
   * Export the users in a Document
   * @param userManager 
   * 
   * @return
   */
  Document exportUsers();

  /**
   * Validate the xml to the correct XSD.
   * @param doc 
   * 
   * @return is Valid xml
   * @throws IOException 
   * @throws DocumentException 
   */
  boolean validate(InputStream doc);

  /**
   * Import users
   * 
   * @param inputStream
   * @param previousDomain
   * @param isUserProviderReadOnly
   * @return
   */
  List<String> importUsers(InputStream inputStream, String previousDomain, boolean isUserProviderReadOnly);

}
