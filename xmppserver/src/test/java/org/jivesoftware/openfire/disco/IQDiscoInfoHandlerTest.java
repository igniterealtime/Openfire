package org.jivesoftware.openfire.disco;

import org.junit.Assert;
import org.junit.Test;

import org.xmpp.forms.DataForm;
import java.util.Set;
/**
 * Unit tests that verify the functionality of {@link IQDiscoInfoHandler}.
 *
 * @author Manasse Ngudia, ngudiamanasse@gmail.com
 */
public class IQDiscoInfoHandlerTest
{
    /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * is null returns null.
     */
    @Test
    public void testGetFirstDataFormNull() throws Exception
    {
        // Setup fixture.
        final Set<DataForm> dataForms = null;

        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertTrue( result == null);
    }
}
