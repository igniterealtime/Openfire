/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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
