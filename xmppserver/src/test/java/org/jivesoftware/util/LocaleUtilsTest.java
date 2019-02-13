package org.jivesoftware.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class LocaleUtilsTest {

    @Test
    public void getLocalizedStringWillReturnASensibleDefaultValue() {

        final String key = "if.this.key.exists.the.test.will.fail";

        assertThat(LocaleUtils.getLocalizedString(key), is("???" + key + "???"));
    }
}
