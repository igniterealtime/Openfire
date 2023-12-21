/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for code used by CertificateManager.
 *
 * @author Gaston Dombiak
 */
public class CertificateTest {

    /**
     * Verify that all CN elements are found.
     */
    @Test
    public void testCN() {
        Pattern cnPattern = Pattern.compile("(?i)(cn=)([^,]*)");
        String text = "EMAILADDRESS=XXXXX@scifi.com, CN=scifi.com, CN=jabber.scifi.com, OU=Domain validated only, O=XX, L=Skx, C=SE";
        List<String> found = new ArrayList<>();
        Matcher matcher = cnPattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(2));
        }
        assertEquals(2, found.size(), "Incorrect number of CNs were found");
        assertEquals("scifi.com" , found.get(0), "Incorrect CN found");
        assertEquals("jabber.scifi.com" , found.get(1), "Incorrect CN found");
    }
}
