package org.jivesoftware.util.property;

import org.junit.Test;


import static org.jivesoftware.util.property.Property.of;
import static org.junit.Assert.assertEquals;

public class IntegerPropertyTest {

    @Test
    public void shouldBeInitializable() {
        final IntegerProperty unit = of("keyThatIsNotInAPropertyStore", 42);
        assertEquals(Integer.valueOf(42), unit.get());
    }

    @Test
    public void valueShouldBeRenderedWithinToString() {
        final IntegerProperty unit = of("keyThatIsNotInAPropertyStore", 42);
        assertEquals("keyThatIsNotInAPropertyStore = 42", unit.toString());
    }
}