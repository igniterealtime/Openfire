package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the Bind2InlineHandler registration and processing functionality.
 */
public class Bind2InlineHandlerTest {

    private Bind2InlineHandler mockHandler;
    private Element mockBoundElement;
    private Element mockFeatureElement;

    @BeforeEach
    public void setUp() {
        mockHandler = mock(Bind2InlineHandler.class);
        mockBoundElement = DocumentHelper.createElement("bound");
        mockFeatureElement = DocumentHelper.createElement("feature");
        mockFeatureElement.addNamespace("test", "http://test.namespace");
    }

    @AfterEach
    public void tearDown() {
        // Clean up any registered handlers to avoid test interference
        Bind2Request.unregisterElementHandler("http://test.namespace");
    }

    @Test
    public void testRegisterElementHandler() {
        // Setup
        when(mockHandler.getNamespace()).thenReturn("http://test.namespace");

        // Execute
        assertDoesNotThrow(() -> Bind2Request.registerElementHandler(mockHandler));

        // Verify registration was successful by attempting to unregister
        Bind2InlineHandler removed = Bind2Request.unregisterElementHandler("http://test.namespace");
        assertNotNull(removed);
        assertEquals(mockHandler, removed);
    }

    @Test
    public void testRegisterNullHandler() {
        // Execute & Verify
        assertThrows(NullPointerException.class, () -> 
            Bind2Request.registerElementHandler(null));
    }

    @Test
    public void testRegisterHandlerWithNullNamespace() {
        // Setup
        when(mockHandler.getNamespace()).thenReturn(null);

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            Bind2Request.registerElementHandler(mockHandler));
    }

    @Test
    public void testRegisterHandlerWithEmptyNamespace() {
        // Setup
        when(mockHandler.getNamespace()).thenReturn("");

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            Bind2Request.registerElementHandler(mockHandler));
    }

    @Test
    public void testRegisterHandlerWithWhitespaceNamespace() {
        // Setup
        when(mockHandler.getNamespace()).thenReturn("   ");

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            Bind2Request.registerElementHandler(mockHandler));
    }

    @Test
    public void testUnregisterElementHandler() {
        // Setup
        when(mockHandler.getNamespace()).thenReturn("http://test.namespace");
        Bind2Request.registerElementHandler(mockHandler);

        // Execute
        Bind2InlineHandler removed = Bind2Request.unregisterElementHandler("http://test.namespace");

        // Verify
        assertNotNull(removed);
        assertEquals(mockHandler, removed);
        
        // Verify it's actually gone
        Bind2InlineHandler removedAgain = Bind2Request.unregisterElementHandler("http://test.namespace");
        assertNull(removedAgain);
    }

    @Test
    public void testUnregisterNonExistentHandler() {
        // Execute
        Bind2InlineHandler removed = Bind2Request.unregisterElementHandler("http://nonexistent.namespace");

        // Verify
        assertNull(removed);
    }

    @Test
    public void testUnregisterWithNullNamespace() {
        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            Bind2Request.unregisterElementHandler(null));
    }

    @Test
    public void testUnregisterWithEmptyNamespace() {
        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            Bind2Request.unregisterElementHandler(""));
    }

    @Test
    public void testReplaceHandler() {
        // Setup
        Bind2InlineHandler firstHandler = mock(Bind2InlineHandler.class);
        Bind2InlineHandler secondHandler = mock(Bind2InlineHandler.class);
        when(firstHandler.getNamespace()).thenReturn("http://test.namespace");
        when(secondHandler.getNamespace()).thenReturn("http://test.namespace");

        // Execute
        Bind2Request.registerElementHandler(firstHandler);
        Bind2Request.registerElementHandler(secondHandler); // Should replace first

        Bind2InlineHandler retrieved = Bind2Request.unregisterElementHandler("http://test.namespace");

        // Verify
        assertEquals(secondHandler, retrieved);
        assertNotEquals(firstHandler, retrieved);
    }
}
