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
 * Unlike the tests in {@link PluginLoadingSimpleHierarchyTest} the tests in this class all are based on the same set
 * of installed plugins. Each of the tests operates on a set of five plugins that exist in a particular parent/child
 * hierarchy:
 *
 * <ul>
 * <li>Node_A that has two children, Node_A_B and Node_A_C</li>
 * <li>Node_A_C that has two child nodes, Node_A_C_D and Node_A_C_E.</li>
 * </ul>
 *
 * This is a diagram of the hierarchy:
 * <pre>
 * {@code
 * Node_A
 * ├── Node_A_B
 * └── Node_A_C
 *     ├── Node_A_C_D
 *     └── Node_A_C_E
 * }
 * </pre>
 *
 * In the `initializePlugin()` as well as in the `destroyPlugin()` methods of each plugin, a reference is made to a
 * class that is provided by any parent plugin. For example, Node_A_C_E references a class provided by Node_A_C, but it
 * also references a class provided by Node_A. These references are created to test  class loader behavior when
 * loading/unloading plugins.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class PluginLoadingComplexHierarchyTest
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

        // Populate the plugin manager with all five plugins from the hierarchy
        if (!installPlugin("node_a")) {
            throw new AssertionFailedException("Unable to execute test: plugin 'node_a.jar' could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_b")) {
            throw new AssertionFailedException("Unable to execute test: plugin 'node_a_b.jar' could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_c")) {
            throw new AssertionFailedException("Unable to execute test: plugin 'node_a_c.jar' could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_c_d")) {
            throw new AssertionFailedException("Unable to execute test: plugin 'node_a_c_d.jar' could not be installed during the test fixture setup.");
        }
        if (!installPlugin("node_a_c_e")) {
            throw new AssertionFailedException("Unable to execute test: plugin 'node_a_c_e.jar' could not be installed during the test fixture setup.");
        }
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
     * Asserts that a plugin is:
     *
     * <ul>
     * <li>not loaded (instance of the class does not exist)</li>
     * <li>not extracted (the 'plugins' directory does not contain a subdirectory that matches the name of the plugin)</li>
     * <li>not installed (the 'plugins' directory does not contain a jar file for the plugin</li>
     * </ul>
     *
     * This is an expected state for a plugin that has not been installed at all, or was explicitly deleted.
     *
     * @param canonicalPluginName The canonical name of the plugin
     */
    private void assertIsRemoved(final String canonicalPluginName) {
        assertFalse(pluginManager.isLoaded(canonicalPluginName), "An instance of the '" + canonicalPluginName + "' plugin class file is not expected to be registered with the plugin manager (but it is).");
        assertFalse(pluginManager.isExtracted(canonicalPluginName), "Expected the 'plugins' directory to not contain a subdirectory that matches the canonical name of plugin '" + canonicalPluginName + "' (but it does).");
        assertFalse(pluginManager.isInstalled(canonicalPluginName), "Expected the 'plugins' directory to not contain the jar file of plugin '" + canonicalPluginName + "' (but it does).");
    }

    /**
     * Asserts that a plugin is:
     *
     * <ul>
     * <li>not loaded (instance of the class does not exist)</li>
     * <li>is installed (the 'plugins' directory does contain a jar file for the plugin</li>
     * </ul>
     *
     * This is an expected state for a plugin that has been installed, but for which a precondition fails (eg: it misses
     * a parent plugin, or it requires a different version of Openfire).
     *
     * @param canonicalPluginName The canonical name of the plugin
     */
    private void assertIsInactive(final String canonicalPluginName) {
        assertFalse(pluginManager.isLoaded(canonicalPluginName), "An instance of the '" + canonicalPluginName + "' plugin class file is not expected to be registered with the plugin manager (but it is).");
        // TODO Do we care about the plugin being extracted? Maybe just remove this line? assertTrueOrFalse(pluginManager.isExtracted("node_a_b")");
        assertTrue(pluginManager.isInstalled(canonicalPluginName), "Expected the 'plugins' directory to contain the jar file of plugin '" + canonicalPluginName + "' (but it does not).");
    }

    /**
     * Asserts that a plugin is:
     *
     * <ul>
     * <li>loaded (instance of the class does not exist)</li>
     * <li>extracted (the 'plugins' directory does not contain a subdirectory that matches the name of the plugin)</li>
     * <li>installed (the 'plugins' directory does not contain a jar file for the plugin</li>
     * </ul>
     *
     * This is an expected state for a plugin that has not been installed at all, or was explicitly deleted.
     *
     * @param canonicalPluginName The canonical name of the plugin
     */
    private void assertIsActive(final String canonicalPluginName) {
        assertTrue(pluginManager.isInstalled(canonicalPluginName), "Expected the 'plugins' directory to contain the jar file of plugin '" + canonicalPluginName + "' (but it does not).");
        assertTrue(pluginManager.isExtracted(canonicalPluginName), "Expected the 'plugins' directory to contain a subdirectory that matches the canonical name of plugin '" + canonicalPluginName + "' (but it does not).");
        assertTrue(pluginManager.isLoaded(canonicalPluginName), "An instance of the '" + canonicalPluginName + "' plugin class file is expected to be registered with the plugin manager (but it is not).");
    }

    /**
     * Verifies that a parent plugin can be deleted, which causes a child plugin to be unloaded too.
     *
     * Note that it is expected that the JAR file of the parent plugin is removed (as the parent is uninstalled) while
     * the JAR file of the child remains. This allows the child to be loaded again when a new parent is installed.
     */
    @Test
    public void testDelete_Node_A() throws Exception
    {
        // Execute system under test.
        pluginManager.deletePlugin("node_a");

        // Verify results.
        assertIsRemoved("node_a");
        assertIsInactive("node_a_b");
        assertIsInactive("node_a_c");
        assertIsInactive("node_a_c_d");
        assertIsInactive("node_a_c_e");
    }

    /**
     * Verifies that when the parent plugin is reloaded, all plugins in the hierarchy eventually are active again.
     */
    @Test
    public void testReload_Node_A() throws Exception
    {
        // Execute system under test.
        pluginManager.reloadPlugin("node_a", true);

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    @Test
    public void testDelete_Node_A_B() throws Exception
    {
        // Execute system under test.
        pluginManager.deletePlugin("node_a_b");

        // Verify results.
        assertIsActive("node_a");
        assertIsRemoved("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    /**
     * Verifies that when the leaf node A_B plugin is reloaded, all plugins in the hierarchy eventually are active again.
     */
    @Test
    public void testReload_Node_A_B() throws Exception
    {
        // Execute system under test.
        pluginManager.reloadPlugin("node_a_b", true);

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    @Test
    public void testDelete_Node_A_C() throws Exception
    {
        // Execute system under test.
        pluginManager.deletePlugin("node_a_c");

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsRemoved("node_a_c");
        assertIsInactive("node_a_c_d");
        assertIsInactive("node_a_c_e");
    }

    /**
     * Verifies that when the intermediate node A_C plugin is reloaded, all plugins in the hierarchy eventually are active again.
     */
    @Test
    public void testReload_Node_A_C() throws Exception
    {
        // Execute system under test.
        pluginManager.reloadPlugin("node_a_c", true);

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    @Test
    public void testDelete_Node_A_C_D() throws Exception
    {
        // Execute system under test.
        pluginManager.deletePlugin("node_a_c_d");

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsRemoved("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    /**
     * Verifies that when the leaf node A_C_D plugin is reloaded, all plugins in the hierarchy eventually are active again.
     */
    @Test
    public void testReload_Node_A_C_D() throws Exception
    {
        // Execute system under test.
        pluginManager.reloadPlugin("node_a_c_d", true);

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
    }

    @Test
    public void testDelete_Node_A_C_E() throws Exception
    {
        // Execute system under test.
        pluginManager.deletePlugin("node_a_c_e");

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsRemoved("node_a_c_e");
    }

    /**
     * Verifies that when the leaf node A_C_E plugin is reloaded, all plugins in the hierarchy eventually are active again.
     */
    @Test
    public void testReload_Node_A_C_E() throws Exception
    {
        // Execute system under test.
        pluginManager.reloadPlugin("node_a_c_e", true);

        // Verify results.
        assertIsActive("node_a");
        assertIsActive("node_a_b");
        assertIsActive("node_a_c");
        assertIsActive("node_a_c_d");
        assertIsActive("node_a_c_e");
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
