/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.management.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link StatisticDynamicMBean}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StatisticDynamicMBeanTest
{
    private Statistic statMock;
    private StatisticDynamicMBean systemUnderTest;

    @BeforeEach
    void setup()
    {
        statMock = Mockito.mock(Statistic.class);
        Mockito.when(statMock.getName()).thenReturn("ActiveUsers");
        Mockito.when(statMock.getDescription()).thenReturn("Number of active user sessions");
        Mockito.when(statMock.getUnits()).thenReturn("users");
        Mockito.when(statMock.getStatType()).thenReturn(Statistic.Type.amount);
        Mockito.when(statMock.isPartialSample()).thenReturn(false);
        Mockito.when(statMock.sample()).thenReturn(42.0);
        systemUnderTest = new StatisticDynamicMBean(statMock);
    }

    // ------------------------------------------------------------
    // Attribute Access Tests
    // ------------------------------------------------------------

    @Test
    void testGetAttributeReturnsExpectedValues() throws Exception
    {
        assertEquals("ActiveUsers", systemUnderTest.getAttribute("Name"));
        assertEquals("Number of active user sessions", systemUnderTest.getAttribute("Description"));
        assertEquals("users", systemUnderTest.getAttribute("Units"));
        assertEquals("amount", systemUnderTest.getAttribute("Type"));
        assertEquals(false, systemUnderTest.getAttribute("IsPartialSample"));
        assertEquals(42.0, (double) systemUnderTest.getAttribute("Sample"), 0.0001);
    }

    @Test
    void testGetAttributeThrowsForUnknownAttribute()
    {
        assertThrows(AttributeNotFoundException.class, () -> systemUnderTest.getAttribute("NonExistent"));
    }

    // ------------------------------------------------------------
    // Read-only Enforcement
    // ------------------------------------------------------------

    @Test
    void testSetAttributeAlwaysThrows()
    {
        assertThrows(AttributeNotFoundException.class,
            () -> systemUnderTest.setAttribute(new Attribute("Name", "test")));
    }

    @Test
    void testSetAttributesAlwaysReturnsEmptyList()
    {
        // Setup test fixture.
        final AttributeList input = new AttributeList();
        input.add(new Attribute("Name", "foo"));

        // Execute system under test.
        final AttributeList result = systemUnderTest.setAttributes(input);

        // Verify result.
        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------
    // Bulk Attribute Retrieval
    // ------------------------------------------------------------

    @Test
    void testGetAttributesReturnsValues()
    {
        // Execute system under test.
        final AttributeList result = systemUnderTest.getAttributes(new String[]{"Name", "Units", "Sample"});

        // Verify result.
        assertEquals(3, result.size());
        assertEquals("ActiveUsers", ((Attribute) result.get(0)).getValue());
    }

    @Test
    void testGetAttributesIgnoresUnknownAttributes()
    {
        // Execute system under test.
        final AttributeList result = systemUnderTest.getAttributes(new String[]{"Name", "Unknown"});

        // Verify result.
        assertEquals(1, result.size());
    }

    // ------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------

    @Test
    void testInvokeSampleCallsStatisticSample()
    {
        // Setup test fixture.
        Mockito.when(statMock.sample()).thenReturn(99.9);

        // Execute system under test.
        final Object result = systemUnderTest.invoke("sample", null, null);

        // Verify result.
        assertEquals(99.9, (double) result, 0.0001);
        Mockito.verify(statMock).sample();
    }

    @Test
    void testInvokeUnknownOperationReturnsNull()
    {
        assertNull(systemUnderTest.invoke("reset", null, null));
    }

    // ------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------

    @Test
    void testMBeanInfoContainsExpectedAttributesAndOperations()
    {
        // Execute system under test.
        final MBeanInfo result = systemUnderTest.getMBeanInfo();

        // Verify result.
        assertEquals("Dynamic view of org.jivesoftware.openfire.stats.Statistic", result.getDescription());
        assertEquals(6, result.getAttributes().length);
        assertEquals(1, result.getOperations().length);

        boolean hasSampleOperation = false;
        for (MBeanOperationInfo op : result.getOperations()) {
            if ("sample".equals(op.getName())) {
                hasSampleOperation = true;
                break;
            }
        }
        assertTrue(hasSampleOperation, "Should expose a 'sample' operation");
    }
}
