package org.jivesoftware.openfire.plugin;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.fileupload.FileItem;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.dom4j.io.SAXWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

import com.sun.msv.reader.util.GrammarLoader;
import com.sun.msv.reader.util.IgnoreController;
import com.sun.msv.verifier.DocumentDeclaration;
import com.sun.msv.verifier.Verifier;

public class UserSchemaValidator {
	
	private static final Logger Log = LoggerFactory.getLogger(UserSchemaValidator.class);
	
    private Document doc;
    private String schema;
    
    UserSchemaValidator(FileItem usersFile, String schemaFile) throws DocumentException, IOException {
        SAXReader reader = new SAXReader();
        doc = reader.read(usersFile.getInputStream());
        
        URL schemaURL = this.getClass().getClassLoader().getResource(schemaFile); 
        schema = schemaURL.toExternalForm();
    }

    boolean validate() {
        try {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            DocumentDeclaration docDeclaration = GrammarLoader.loadVGM(schema, new IgnoreController() {
                @Override
				public void error(Locator[] locations,
                                  String message,
                                  Exception exception) {
                    Log.error("ERROR: " + message);
                }

                public void error(Locator[] locations, String message) {
                    Log.error("WARNING: " + message);
                }
            }, saxFactory);
        
            ValidatorErrorHandler validatorErrorHandler = new ValidatorErrorHandler();
            Verifier verifier = new Verifier(docDeclaration, validatorErrorHandler);

            SAXWriter writer = new SAXWriter((ContentHandler) verifier);
            writer.setErrorHandler(validatorErrorHandler);

            writer.write(doc);
            if (verifier.isValid()) {
                return true;
            } else {
                Log.error(doc.getName() + " is invalid.");
                return false;
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return false;
        }
    }
    
    private class ValidatorErrorHandler implements ErrorHandler {
        public void error(SAXParseException e) {
            Log.error("ERROR:" + e);
        }

        public void fatalError(SAXParseException e) {
            Log.error("Fatal:" + e);
        }

        public void warning(SAXParseException e) {
            Log.error("Warning:" + e);
        }
    }
}
