/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LocaleUtilsTest {

    @Test
    public void getLocalizedStringWillReturnASensibleDefaultValue() {

        final String key = "if.this.key.exists.the.test.will.fail";

        assertThat(LocaleUtils.getLocalizedString(key), is("???" + key + "???"));
    }

    @Test
    public void bestMatchingSupportedLocale() {
        Enumeration<Locale> reqLocales = Collections.enumeration(asList(
            new Locale("pt", "BR"),
            new Locale("pt", "PT"),
            new Locale("pt")
            ));
        String preferredLocale = LocaleUtils.bestMatchingSupportedLocale(reqLocales);
        assertThat(preferredLocale, is("pt_BR"));

        reqLocales = Collections.enumeration(asList(
            new Locale("pt")
        ));
        preferredLocale = LocaleUtils.bestMatchingSupportedLocale(reqLocales);
        assertThat(preferredLocale, is("pt_PT"));
    }
}
