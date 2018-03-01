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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * An utility to convert a file to XML, and validate the XML against a set of XML Schema files.
 * 
 * @author Anno van Vliet
 *
 */
public class UserSchemaValidator {

  private static final Logger Log = LoggerFactory.getLogger(UserSchemaValidator.class);

  private static final CharSequence STRICT_DECLARATION_MSG = "The matching wildcard is strict, but no declaration can be found for element";

  private final InputStream source;
  private final Source[] schemaSources;

  /**
   * Construct a Validator object which parses and validates a input source.
   * 
   * @param source XML input document
   * @param schemaFile zero or more schema files.
   */
  UserSchemaValidator(InputStream source, String... schemaFile) {
    this.source = source;
    List<Source> sourceList = new ArrayList<Source>();

    for (String schema : schemaFile) {
      try {
        URL schemaURL = this.getClass().getClassLoader().getResource(schema);
        if (schemaURL != null) {
          sourceList.add(new StreamSource(schemaURL.openStream()));
        } else {
          Log.warn("Cannot find schema definition " + schema);
        }
      } catch (IOException e) {
        Log.warn("Cannot open schema definition " + schema + " : " + e.getMessage());
        Log.debug("", e);
      }
    }
    
    schemaSources = new Source[sourceList.size()];
    sourceList.toArray(schemaSources);

  }

  /**
   * Perform a validate and a parse of the specified source document.
   * Validating is done with the specified schemafiles.
   * 
   * @return A Document when the validation s successful.
   */
  Document validateAndParse() {
    ValidatorErrorHandler handler = new ValidatorErrorHandler();
    try {
      
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

      documentBuilderFactory.setNamespaceAware(true);
      documentBuilderFactory.setXIncludeAware(true);
      documentBuilderFactory.setValidating(false);
      // We don't want xml:base and xml:lang attributes in the output.
      documentBuilderFactory.setFeature(
                      "http://apache.org/xml/features/xinclude/fixup-base-uris", false);
      documentBuilderFactory.setFeature(
                      "http://apache.org/xml/features/xinclude/fixup-language", false);

      if ( schemaSources.length > 0 ) {
        Log.info("Checking Schema's");

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        schemaFactory.setErrorHandler(handler);
        
        Schema schema = schemaFactory.newSchema(schemaSources);

        documentBuilderFactory.setSchema(schema);
        
        
        Log.info("Start validating document");

      } else {
        Log.info("Loading document");
      }
      
      DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

      handler.reset();
      
      // Communicate some info about the Xincludes in the imported file. These imports should be resolvable by the server. 
      documentBuilder.setEntityResolver( new EntityResolver() {
        
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
          Log.info(String.format("resolved Entity:%s %s", (publicId != null ? publicId : "") , systemId));
          return null;
        }
      } );
      
      documentBuilder.setErrorHandler(handler);
      Document result = documentBuilder.parse(source);

      if (result != null && handler.isValid()) {
        return result;
      } else {
        Log.warn(String.format("document is invalid. %1$d errors found.", handler.getNrOfErrors()));
        return null;
      }
    } catch (Exception e) {
      Log.warn(String.format("document validation failed. %1$d errors found.", handler.getNrOfErrors()));
      Log.debug("", e);
      return null;
    }
  }

  private class ValidatorErrorHandler implements ErrorHandler {
    private int nrOfErrors = 0;

    public void error(SAXParseException e) {

      if (e.getMessage().contains(STRICT_DECLARATION_MSG)) {
        Log.warn("This error indicates there is no XML Schema to validate the refered element: " + e.getLocalizedMessage());
      } else {
        Log.warn("ERROR:" + e.getLocalizedMessage());
        nrOfErrors++;
      }
    }

    public void fatalError(SAXParseException e) {
      Log.error("Fatal:" + e.getLocalizedMessage());
      nrOfErrors++;
    }

    public void warning(SAXParseException e) {
      Log.error("Warning:" + e.getLocalizedMessage());
    }

    public void reset() {
      nrOfErrors = 0;
    }

    public boolean isValid() {
      return nrOfErrors == 0;
    }

    /**
     * @return the nrOfErrors
     */
    public int getNrOfErrors() {
      return nrOfErrors;
    }

  }
}
