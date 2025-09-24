/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.container;

import com.mysql.cj.exceptions.AssertionFailedException;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify the functionality of {@link PluginManager}, particularly in regard to loading and unloading of
 * plugins.
 *
 * These tests operate on single plugins, or plugins that exist in a small hierarchy (a parent/child, or parent with two
 * children).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class PluginLoadingSimpleHierarchyTest
{
    /**
     * The system-under-test. A new instance is created before each test starts.
     */
    private PluginManager pluginManager;

    /**
     * The directory used as the base directory for the plugin manager. This is recreated to a new, empty directory
     * before each test starts.
     */
    private Path tempPluginDirectory;

    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws IOException
    {
        // Create a new, empty directory to be used as the base directory for the plugin manager.
        tempPluginDirectory = Files.createTempDirectory("openfire-unittest-pluginloading");
        tempPluginDirectory.toFile().deleteOnExit();

        // Create a new instance of the plugin manager.
        pluginManager = new PluginManager(tempPluginDirectory);
        pluginManager.start();

        // Mock the XMPPServer instance so that it returns our system-under-test.
        final XMPPServer mockServer = Fixtures.mockXMPPServer();
        Mockito.doReturn(pluginManager).when(mockServer).getPluginManager();
        //noinspection deprecation
        XMPPServer.setInstance(mockServer);
    }

    @AfterEach
    public void tearDown() throws IOException
    {
        // Tear down the system-under-test
        pluginManager.shutdown();

        // Delete the temporary directory used as the plugin-dir.
        if (tempPluginDirectory != null) {
            try (final Stream<Path> paths = Files.walk(tempPluginDirectory)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }

        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that a plugin JAR file can be installed.
     */
    @Test
    public void testLoadPlugin() throws Exception
    {
        // Execute system under test.
        final boolean result = installPlugin("node_a");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(result, "Expected the plugin manager to report success after installation (but it did not).");
    }

    /**
     * Verifies that a plugin can be deleted.
     */
    @Test
    public void testDeletePlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the plugin (that is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.deletePlugin("node_a");

        // Verify results.
        assertFalse(pluginManager.isLoaded("node_a"), "An instance of the plugin's class file is no longer expected to be registered with the plugin manager (but it is).");
        assertFalse(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to no longer contain a subdirectory that matches the canonical name of the plugin (but it does).");
        assertFalse(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to no longer contain the plugin jar file (but it does).");
    }

    /**
     * Verifies that a plugin JAR file can be reloaded.
     */
    @Test
    public void testReloadPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the plugin (that is to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.reloadPlugin("node_a");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a plugin JAR file cannot be fully installed when its designated parent plugin is not installed.
     */
    @Test
    public void testLoadChildWithoutParentPlugin() throws Exception
    {
        // Execute system under test.
        installPlugin("node_a_b");

        // Verify results.
        assertFalse(pluginManager.isLoaded("node_a_b"), "An instance of the plugin's class file is not expected to be registered with the plugin manager (but it is).");
        // TODO Do we care about the plugin being extracted? Maybe just remove this line? assertTrueOrFalse(pluginManager.isExtracted("node_a_b")");
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the plugin jar file (but it does not) even if it cannot be loaded (it should be present in case the parent plugin gets installed immediately afterwards).");
    }

    /**
     * Verifies that a child plugin JAR file can be fully installed when its designated parent plugin is installed.
     */
    @Test
    public void testLoadChildWithParentPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (of the plugin to be installed as part of this test) could not be installed during the test fixture setup.");
        }
        // Execute system under test.
        final boolean result = installPlugin("node_a_b");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(result, "Expected the plugin manager to report success after installation (but it did not).");
    }

    /**
     * Verifies that a child plugin can be deleted, while the parent plugin remains installed (which may mean it gets
     * reinstalled/reloaded automatically).
     */
    @Test
    public void testDeleteChildWithParentPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (of the plugin to be deleted as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: the child plugin (that is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.deletePlugin("node_a_b");

        // Verify results.
        assertFalse(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is no longer expected to be registered with the plugin manager (but it is).");
        assertFalse(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to no longer contain a subdirectory that matches the canonical name of the child plugin (but it does).");
        assertFalse(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to no longer contain the child plugin jar file (but it does).");
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the parent plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the parent plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the parent plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a child plugin JAR file can be fully reloaded when its designated parent plugin is installed.
     */
    @Test
    public void testReloadChildWithParentPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (of the plugin to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: the child plugin (that is to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.reloadPlugin("node_a_b");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a parent plugin can be deleted, which causes a child plugin to be unloaded too.
     *
     * Note that it is expected that the JAR file of the parent plugin is removed (as the parent is uninstalled) while
     * the JAR file of the child remains. This allows the child to be loaded again when a new parent is installed.
     */
    @Test
    public void testDeleteParentWithChildPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (that is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: the child plugin (of which the parent is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.deletePlugin("node_a");

        // Verify results.
        assertFalse(pluginManager.isLoaded("node_a"), "An instance of the parent plugin's class file is no longer expected to be registered with the plugin manager (but it is).");
        assertFalse(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to no longer contain a subdirectory that matches the canonical name of the parent plugin (but it does).");
        assertFalse(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to no longer contain the parent plugin jar file (but it does).");

        assertFalse(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is not expected to be registered with the plugin manager (but it is).");
        // TODO Do we care about the plugin being extracted? Maybe just remove this line? assertTrueOrFalse(pluginManager.isExtracted("node_a_b")");
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the child plugin jar file (but it does not) even if it cannot be loaded (it should be present in case the parent plugin gets installed immediately afterwards).");
    }

    /**
     * Verifies that a parent plugin can be fully reloaded which causes a child plugin to remain functional (which may
     * mean it gets reinstalled/reloaded automatically).
     */
    @Test
    public void testReloadParentWithChildPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: the child plugin (of which the parent is to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.reloadPlugin("node_a");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the parent plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the parent plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the parent plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a child plugin can be deleted, while the parent plugin and other child plugins of that parent
     * remain installed (which may mean it gets reinstalled/reloaded automatically).
     */
    @Test
    public void testDeleteChildWithSiblingPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (of the plugin to be deleted as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: a child plugin (that is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_c")) {
            throw new AssertionFailedException("Unable to execute test: a child plugin (that is a sibling of the plugin to be deleted as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.deletePlugin("node_a_b");

        // Verify results.
        assertFalse(pluginManager.isLoaded("node_a_b"), "An instance of the deleted child plugin's class file is no longer expected to be registered with the plugin manager (but it is).");
        assertFalse(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to no longer contain a subdirectory that matches the canonical name of the deleted child plugin (but it does).");
        assertFalse(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to no longer contain the deleted child plugin jar file (but it does).");
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the parent plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the parent plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the parent plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(pluginManager.isInstalled("node_a_c"), "Expected the 'plugins' directory to contain the sibling child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_c"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the sibling child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_c"), "An instance of the sibling child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a child plugin can be reloaded, while the parent plugin and other child plugins of that parent
     * remain installed (which may mean it gets reinstalled/reloaded automatically).
     */
    @Test
    public void testReloadChildWithSiblingPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (of the plugin to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: a child plugin (that is to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_c")) {
            throw new AssertionFailedException("Unable to execute test: a child plugin (that is a sibling of the plugin to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }

        // Execute system under test.
        pluginManager.reloadPlugin("node_a_b");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the reloaded child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the reloaded child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_b"), "An instance of the reloaded child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the parent plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the parent plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the parent plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(pluginManager.isInstalled("node_a_c"), "Expected the 'plugins' directory to contain the sibling child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_c"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the sibling child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_c"), "An instance of the sibling child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Verifies that a reinstalling a deleted parent plugin causes a child plugin (that is not explictly being
     * reinstalled) to be automatically reinstalled too.
     */
    @Test
    public void testRestoreParentWithChildPlugin() throws Exception
    {
        // Setup test fixture.
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: the parent plugin (that is to be reloaded as part of this test) could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: the child plugin (of which the parent is to be deleted as part of this test) could not be installed during the test fixture setup.");
        }
        pluginManager.deletePlugin("node_a");

        // Execute system under test.
        final boolean result = installPlugin("node_a");

        // Verify results.
        assertTrue(pluginManager.isInstalled("node_a"), "Expected the 'plugins' directory to contain the (reinstalled) parent plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the (reinstalled) parent plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a"), "An instance of the (reinstalled) parent plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
        assertTrue(result, "Expected the plugin manager to report success after (re)installation of the parent plugin (but it did not).");
        assertTrue(pluginManager.isInstalled("node_a_b"), "Expected the 'plugins' directory to contain the child plugin jar file (but it does not).");
        assertTrue(pluginManager.isExtracted("node_a_b"), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of the child plugin (but it does not).");
        assertTrue(pluginManager.isLoaded("node_a_b"), "An instance of the child plugin's class file is expected to have been created and registered with the plugin manager (but it has not).");
    }

    /**
     * Utility method to remove boilerplate code to install a plugin using PluginManager
     *
     * @param canonicalPluginName The name of the plugin that is to be installed. A jar file is expected to exist in <code>/testplugins/NAME.jar</code>
     * @return the result of the invocation of {@link PluginManager#installPlugin(InputStream, String)}
     */
    private boolean installPlugin(final String canonicalPluginName) throws IOException
    {
        final String fileName = canonicalPluginName + ".jar";
        try (final InputStream is = this.getClass().getResourceAsStream("/testplugins/" + fileName)) {
            return pluginManager.installPlugin(is, fileName);
        }
    }
}
