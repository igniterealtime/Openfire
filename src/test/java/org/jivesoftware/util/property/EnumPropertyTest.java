package org.jivesoftware.util.property;

import org.jivesoftware.openfire.Connection;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.cert.CRLReason;

import static org.jivesoftware.util.property.Property.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnumPropertyTest {

    @Test
    public void shouldBeInitializableWithClassicEnum() {
        final EnumProperty<CRLReason> unit = of("keyThatIsNotInAPropertyStore", CRLReason.UNSPECIFIED);
        assertEquals(CRLReason.UNSPECIFIED, unit.get());
    }

    @Test
    public void shouldBeInitializableWithInnerEnum() {
        final EnumProperty<Connection.CompressionPolicy> unit = of("keyThatIsNotInAPropertyStore", Connection.CompressionPolicy.optional);
        assertEquals(Connection.CompressionPolicy.optional, unit.get());
    }

    @Test
    public void valueShouldBeRenderedWithinToString() {
        final EnumProperty<Connection.CompressionPolicy> unit = of("keyThatIsNotInAPropertyStore", Connection.CompressionPolicy.optional);
        assertEquals("keyThatIsNotInAPropertyStore = CompressionPolicy : optional", unit.toString());
    }

    /**
     * Ohh dear, we need a good and simple mocking framework for verification -  Like Mockito!!
     */
    @Test
    public void shouldFirePropertyChangeListener() {
        final TestHelper helper = new TestHelper();
        final EnumProperty<Connection.CompressionPolicy> unit = of("keyThatIsNotInAPropertyStore", Connection.CompressionPolicy.optional);
        unit.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                helper.propertyChangeFired = true;
            }
        });

        unit.set(Connection.CompressionPolicy.disabled);
        assertTrue(helper.propertyChangeFired);
    }

    private static class TestHelper {
        public boolean propertyChangeFired = false;
    }
}