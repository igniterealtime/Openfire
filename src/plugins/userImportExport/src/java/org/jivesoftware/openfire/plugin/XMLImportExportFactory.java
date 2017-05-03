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

/**
 * Factory to instantiate the correct InExporter.
 *
 * @author Anno van Vliet
 *
 */
public class XMLImportExportFactory {

  /**
   * @param xep227Support
   * @return
   */
  public static InExporter getExportInstance(boolean xep227Support) {
    
    if ( xep227Support ) {
      return new Xep227Exporter();
    } else {
      return new OpenfireExporter();
    }
  }
}
