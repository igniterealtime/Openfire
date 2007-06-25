/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.plugin;

import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jivesoftware.openfire.plugin.AbstractPluginSupport;
import org.jivesoftware.openfire.plugin.BroadcastPlugin;
import org.jivesoftware.openfire.container.plugin.PluginName;
import org.jivesoftware.openfire.container.plugin.PluginDescription;
import org.jivesoftware.openfire.component.ComponentLifecycle;
import org.jivesoftware.util.JiveProperties;
import org.junit.Test;
import org.xmpp.component.ComponentManager;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.AbstractModule;

import static org.junit.Assert.*;

/**
 *
 */
public class BroadcastPluginTest {
    private static final Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private static final Module pluginModule = new AbstractModule() {
        protected void configure() {
            // not a singleton so we get a fresh one every time.
            bind(BroadcastPlugin.class);
            bind(String.class).annotatedWith(PluginName.class).toInstance("broadcast");
            bind(String.class).annotatedWith(PluginDescription.class)
                    .toInstance("the broadcast plugin");
        }
    };

    private static final Injector injector = AbstractPluginSupport.createInjector(context,
            pluginModule);

    @Test
    public void testPluginInitialize() {
        final BroadcastPlugin broadcastPlugin = injector.getInstance(BroadcastPlugin.class);
        final ComponentLifecycle componentLifecycle = context.mock(ComponentLifecycle.class);
        context.checking(new Expectations() {{
            try {
                allowing(injector.getInstance(JiveProperties.class))
                        .getProperty("plugin.broadcast.serviceName", "broadcast");
                will(returnValue("broadcast"));

                allowing(injector.getInstance(ComponentManager.class))
                        .addComponent("broadcast", broadcastPlugin, null);
                will(returnValue(componentLifecycle));
            }
            catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }});

        broadcastPlugin.initialize();
    }

    @Test
    public void testNameAndDescription() {
        BroadcastPlugin broadcastPlugin = injector.getInstance(BroadcastPlugin.class);
        assertEquals(broadcastPlugin.getName(), "broadcast");
        assertEquals(broadcastPlugin.getDescription(), "the broadcast plugin");
    }

    @Test
    public void testGetAndSetProperties() {
        final BroadcastPlugin broadcastPlugin = injector.getInstance(BroadcastPlugin.class);
        final JiveProperties jiveProperties = injector.getInstance(JiveProperties.class);

        context.checking(new Expectations() {{
            allowing(jiveProperties).put("plugin.broadcast.serviceName", "broadcast");
        }});
        broadcastPlugin.setServiceName("broadcast");
    }

}
