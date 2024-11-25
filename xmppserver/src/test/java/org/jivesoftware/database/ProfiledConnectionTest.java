/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality of {@link ProfiledConnection}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ProfiledConnectionTest
{
    @BeforeEach
    public void resetStatistics() {
        ProfiledConnection.resetStatistics();
    }

    @AfterEach
    public void stopCollecting() {
        ProfiledConnection.stop();
        ProfiledConnection.resetStatistics();
    }

    @Test
    public void testAverageQueryTime() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 4);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 6);
        final double result = ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(5, result);
    }

    @Test
    public void testQueriesPerSecondNotStarted() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.stop();

        // Execute system under test.
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testQueriesPerSecondNoQueries() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testQueriesPerSecondStartedNotStopped() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 4);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 6);
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertTrue(result > 0);
    }

    @Test
    public void testQueriesPerSecondStartedAndStopped() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 4);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 6);
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertTrue(result > 0);
    }

    @Test
    public void testAverageQueryTimeCollectedWhenProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(2, result);
    }

    @Test
    public void testAverageQueryTimeNotCollectedWhenNotProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.stop();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testQueriesPerSecondCollectedWhenProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertNotEquals(0, result);
    }

    @Test
    public void testQueriesPerSecondNotCollectedWhenNotProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.stop();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testQueryCountCollectedWhenProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getQueryCount(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(1, result);
    }

    @Test
    public void testQueryCountNotCollectedWhenNotProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.stop();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getQueryCount(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testTotalQueryTimeCollectedWhenProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(2, result);
    }

    @Test
    public void testTotalQueryTimeNotCollectedWhenNotProfiling() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.stop();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(0, result);
    }

    @Test
    public void testStatsDontResetAtStop() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 3);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 1);
        ProfiledConnection.stop();

        // Execute system under test.
        final double result = ProfiledConnection.getQueryCount(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(2, result);
    }

    @Test
    public void testStartResetsStatistics()
    {
        // Setup test fixture.
        ProfiledConnection.start();
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 3);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 1);
        ProfiledConnection.stop();
        ProfiledConnection.start();

        // Execute system under test.
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 2);
        final double result = ProfiledConnection.getQueryCount(ProfiledConnection.Type.select);

        // Verify results.
        assertEquals(1, result);
    }

    @Test
    public void testQueriesPerSecondCalculatedBasedOnEndTime() throws Exception
    {
        // Setup test fixture.
        ProfiledConnection.start();
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 3);
        ProfiledConnection.addQuery(ProfiledConnection.Type.select, "SELECT 1", 1);
        Thread.sleep(5); // To prevent 'infinity'
        final double expected = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);
        ProfiledConnection.stop();

        // Execute system under test.
        Thread.sleep(50);
        final double result = ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select);

        // Verify results.
        assertTrue(result > expected*.25, "Expected value '" + result + "' to be roughly equal to '" + expected + "', but it was significantly different."); // result will typically be _exact_, but the execution time of getQueriesPerSecond() and stop() may introduce a bit of drift.
        assertTrue(result < expected*2, "Expected value '" + result + "' to be roughly equal to '" + expected + "', but it was significantly different."); // result will typically be _exact_, but the execution time of getQueriesPerSecond() and stop() may introduce a bit of drift.
    }
}
