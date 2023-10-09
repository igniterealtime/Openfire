package com.igniterealtime.openfire.updaterunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Properties;

public class PropertiesReader {
    private Properties properties;

    public PropertiesReader(String propertyFileName) throws IOException, URISyntaxException {
        //String propFilePath = new File(parent, propertyFileName).getAbsolutePath();
        System.out.println(propertyFileName);
        File f = new File(propertyFileName);
        if(!f.exists()){
            throw new IOException("Property file doesn't exist!");
        }
        this.properties = new Properties();

        try (FileInputStream is = new FileInputStream(propertyFileName)) {
            this.properties.load(is);
        }
    }

    public String getProperty(String propertyName) {

        return this.properties.getProperty(propertyName);
    }
}
