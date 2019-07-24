package org.jivesoftware.openfire.disco;

import org.junit.Assert;
import org.junit.Test;

import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.HashSet;
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
        Assert.assertNull(result);
    }

    /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * is an empty (not-null) collection returns a null
     */
    @Test
    public void testGetFirstDataFormEmpty() throws Exception
    {
        // Setup fixture.
        final Set<DataForm> dataForms = new HashSet<>();

        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertNull(result);
    }


    /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * a collection with exactly one item (that is null) returns null.
     */
    @Test
    public void testGetFirstDataFormWithOneNull() throws Exception
    {
        // Setup fixture.
        final DataForm  form = null;
        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(form);

        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertNull(result);
    }

     /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * a collection with exactly one item (that is not null) returns a not null Dataform.
     */
    @Test
    public void testGetFirstDataFormWithOneNotNull() throws Exception
    {
        // Setup fixture.
        final DataForm dataForm = new DataForm(DataForm.Type.result);
        final FormField fieldType = dataForm.addField();
        fieldType.setVariable("FORM_TYPE");
        fieldType.setType(FormField.Type.hidden);
        fieldType.addValue("http://jabber.org/network/serverinfo");
        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(dataForm);

        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertNotNull(result);
    }

    /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * a collection that has two (non-null) items returns a not null Dataform.
     */
    @Test
    public void testGetFirstDataFormWithTwoNotNullAndNull() throws Exception
    {
        // Setup fixture.
        final DataForm dataForm = new DataForm(DataForm.Type.result);
        final FormField fieldType = dataForm.addField();
        fieldType.setVariable("FORM_TYPE");
        fieldType.setType(FormField.Type.hidden);
        fieldType.addValue("http://jabber.org/network/serverinfo");
        final DataForm dataForm1 = null;
        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(dataForm1);
        dataForms.add(dataForm);

        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertNotNull(result);
    }

    /**
     * Test for {@link IQDiscoInfoHandler#getFirstDataForm(dataForms)}. Verifies that an input argument that
     * a collection that has two (no null) items returns the first not null Dataform.
     */
    @Test
    public void testGetFirstDataFormWithTwoNotNull() throws Exception
    {
        // Setup fixture.
        final DataForm dataForm = new DataForm(DataForm.Type.result);
        final FormField fieldType = dataForm.addField();
        fieldType.setVariable("FORM_TYPE");
        fieldType.setType(FormField.Type.hidden);
        fieldType.addValue("http://jabber.org/network/serverinfo");

        final DataForm dataForm1 = new DataForm(DataForm.Type.result);
        final FormField fieldType1 = dataForm1.addField();
        fieldType1.setVariable("FORM_TYPE");
        fieldType1.setType(FormField.Type.hidden);
        fieldType1.addValue("urn:xmpp:dataforms:softwareinfo");

        final Set<DataForm> dataForms = new HashSet<>();
        dataForms.add(dataForm);
        dataForms.add(dataForm1);
        
        // Execute system under test.
        final DataForm result = IQDiscoInfoHandler.getFirstDataForm(dataForms);

        // Verify results.
        Assert.assertNotNull(result);
    }

}
