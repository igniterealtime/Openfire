package org.jivesoftware.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.DefaultAuthProvider;
import org.jivesoftware.openfire.auth.HybridAuthProvider;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.ldap.LdapAuthProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;

@RunWith(MockitoJUnitRunner.class)
public class SystemPropertyTest {

    @Mock
    private XMPPServer xmppServer;
    @Mock
    private PluginManager pluginManager;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @SuppressWarnings({"deprecation", "ResultOfMethodCallIgnored"})
    @Before
    public void setUp() {
        Fixtures.clearExistingProperties();
        doReturn(pluginManager).when(xmppServer).getPluginManager();
        XMPPServer.setInstance(xmppServer);
    }

    @Test
    public void willBuildAStringProperty() {

        final SystemProperty<String> stringProperty = SystemProperty.Builder.ofType(String.class)
            .setKey("a-test-string-property")
            .setDefaultValue("this-is-a-default")
            .setDynamic(true)
            .build();

        assertThat(stringProperty.getValue(), is("this-is-a-default"));
        assertThat(stringProperty.getValueAsSaved(), is("this-is-a-default"));
        stringProperty.setValue("this-is-not-a-default");
        assertThat(stringProperty.getValue(), is("this-is-not-a-default"));
        assertThat(stringProperty.getValueAsSaved(), is("this-is-not-a-default"));
    }

    @Test
    public void willBuildALongProperty() {

        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("a-test-long-property")
            .setDefaultValue(42L)
            .setDynamic(true)
            .build();

        assertThat(longProperty.getValue(), is(42L));
        assertThat(longProperty.getValueAsSaved(), is("42"));
        longProperty.setValue(84L);
        assertThat(longProperty.getValue(), is(84L));
        assertThat(longProperty.getValueAsSaved(), is("84"));
    }

    @Test
    public void willBuildADoubleProperty() {

        final SystemProperty<Double> doubleProperty = SystemProperty.Builder.ofType(Double.class)
            .setKey("a-test-double-property")
            .setDefaultValue(42.2)
            .setDynamic(true)
            .build();

        assertThat(doubleProperty.getValue(), is(42.2));
        assertThat(doubleProperty.getValueAsSaved(), is("42.2"));
        doubleProperty.setValue(84.1);
        assertThat(doubleProperty.getValue(), is(84.1));
        assertThat(doubleProperty.getValueAsSaved(), is("84.1"));
    }

    @Test
    public void willBuildADurationProperty() {

        final SystemProperty<Duration> longProperty = SystemProperty.Builder.ofType(Duration.class)
            .setKey("a-test-duration-property")
            .setDefaultValue(Duration.ofHours(1))
            .setChronoUnit(ChronoUnit.MINUTES)
            .setDynamic(true)
            .build();

        assertThat(longProperty.getValue(), is(Duration.ofHours(1)));
        assertThat(longProperty.getValueAsSaved(), is("60"));
        longProperty.setValue(Duration.ofDays(1));
        assertThat(longProperty.getValue(), is(Duration.ofDays(1)));
        assertThat(JiveGlobals.getProperty("a-test-duration-property"), is("1440"));
        assertThat(longProperty.getValueAsSaved(), is("1440"));
    }

    @Test
    public void willBuildABooleanProperty() {

        final SystemProperty<Boolean> booleanProperty = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("a-test-boolean-property")
            .setDefaultValue(false)
            .setDynamic(true)
            .build();

        assertThat(booleanProperty.getValue(), is(false));
        assertThat(booleanProperty.getValueAsSaved(), is("false"));
        booleanProperty.setValue(true);
        assertThat(booleanProperty.getValue(), is(true));
        assertThat(booleanProperty.getValueAsSaved(), is("true"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildAPropertyWithoutAKey() {
        SystemProperty.Builder.ofType(String.class)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildAPropertyWithoutADefaultValue() {
        SystemProperty.Builder.ofType(String.class)
            .setKey("a-test-property-without-a-default-value")
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildAPropertyWithoutADynamicIndicator() {
        SystemProperty.Builder.ofType(String.class)
            .setKey("a-test-property-without-dynamic-set")
            .setDefaultValue("default value")
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildAPropertyForAnUnsupportedClass() {
        SystemProperty.Builder.ofType(JavaSpecVersion.class)
            .setKey("a-property-for-an-unsupported-class")
            .setDefaultValue(new JavaSpecVersion("1.8"))
            .setDynamic(true)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildTheSamePropertyTwice() {
        SystemProperty.Builder.ofType(String.class)
            .setKey("a-duplicate-property")
            .setDefaultValue("")
            .setDynamic(true)
            .build();
        SystemProperty.Builder.ofType(Boolean.class)
            .setKey("a-duplicate-property")
            .setDefaultValue(false)
            .setDynamic(true)
            .build();
    }

    @Test
    public void willPreventAValueBeingTooLow() {
        final SystemProperty<Long> property = SystemProperty.Builder.ofType(Long.class)
            .setKey("this-is-a-constrained-long-key")
            .setDefaultValue(42L)
            .setMinValue(0L)
            .setMaxValue(42L)
            .setDynamic(true)
            .build();

        property.setValue(-1L);

        assertThat(property.getValue(), is(42L));
        assertThat(property.getValueAsSaved(), is("42"));
    }

    @Test
    public void willPreventAValueBeingTooHigh() {
        final SystemProperty<Long> property = SystemProperty.Builder.ofType(Long.class)
            .setKey("this-is-another-constrained-long-key")
            .setDefaultValue(42L)
            .setMinValue(0L)
            .setMaxValue(42L)
            .setDynamic(true)
            .build();

        property.setValue(500L);

        assertThat(property.getValue(), is(42L));
        assertThat(property.getValueAsSaved(), is("42"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildADurationPropertyWithoutAChronoUnit() {
        SystemProperty.Builder.ofType(Duration.class)
            .setKey("this-will-not-work")
            .setDefaultValue(Duration.ZERO)
            .setDynamic(true)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildADurationPropertyWithAnInvalidChronoUnit() {
        SystemProperty.Builder.ofType(Duration.class)
            .setKey("this-will-not-work")
            .setDefaultValue(Duration.ZERO)
            .setChronoUnit(ChronoUnit.CENTURIES)
            .setDynamic(true)
            .build();
    }

    @Test
    public void willNotifyListenersOfChanges() {

        final AtomicReference<Duration> notifiedValue = new AtomicReference<>();

        final SystemProperty<Duration> property = SystemProperty.Builder.ofType(Duration.class)
            .setKey("property-notifier")
            .setDefaultValue(Duration.ZERO)
            .setChronoUnit(ChronoUnit.SECONDS)
            .setDynamic(true)
            .addListener(notifiedValue::set)
            .build();

        property.setValue(Duration.ofMinutes(60));

        assertThat(notifiedValue.get(), is(Duration.ofHours(1)));
    }

    @Test
    public void willIndicateDynamicPropertyDoesNotNeedRestarting() {

        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("a-test-dynamic-property")
            .setDefaultValue(42L)
            .setDynamic(true)
            .build();

        assertThat(longProperty.isDynamic(), is(true));
        assertThat(longProperty.isRestartRequired(), is(false));
        longProperty.setValue(84L);
        assertThat(longProperty.isRestartRequired(), is(false));
    }

    @Test
    public void willIndicateNonDynamicPropertyNeedsRestarting() {

        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("a-test-non-dynamic-property")
            .setDefaultValue(42L)
            .setDynamic(false)
            .build();

        assertThat(longProperty.isDynamic(), is(false));
        assertThat(longProperty.isRestartRequired(), is(false));
        longProperty.setValue(84L);
        assertThat(longProperty.isRestartRequired(), is(true));
    }

    @Test
    public void theDefaultPluginIsOpenfire() {

        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("an-openfire-property")
            .setDefaultValue(42L)
            .setDynamic(false)
            .build();

        assertThat(longProperty.getPlugin(), is("Openfire"));
    }

    @Test
    public void thePluginCanBeChanged() {

        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("a-plugin-property")
            .setDefaultValue(42L)
            .setPlugin("TestPluginName")
            .setDynamic(false)
            .build();

        assertThat(longProperty.getPlugin(), is("TestPluginName"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPluginIsRequired() {

        SystemProperty.Builder.ofType(Long.class)
            .setKey("a-null-plugin-property")
            .setDefaultValue(42L)
            .setPlugin(null)
            .setDynamic(false)
            .build();
    }

    @Test
    public void willReturnAClass() {

        final SystemProperty<Class> classProperty = SystemProperty.Builder.ofType(Class.class)
            .setKey("a-class-property")
            .setDefaultValue(DefaultAuthProvider.class)
            .setBaseClass(AuthProvider.class)
            .setDynamic(false)
            .build();

        assertThat(classProperty.getValue(), is(equalTo(DefaultAuthProvider.class)));
        assertThat(classProperty.getValueAsSaved(), is("org.jivesoftware.openfire.auth.DefaultAuthProvider"));
        JiveGlobals.setProperty("a-class-property", "org.jivesoftware.openfire.auth.HybridAuthProvider");
        assertThat(classProperty.getValue(), is(equalTo(HybridAuthProvider.class)));
        assertThat(classProperty.getValueAsSaved(), is("org.jivesoftware.openfire.auth.HybridAuthProvider"));
        classProperty.setValue(LdapAuthProvider.class);
        assertThat(classProperty.getValue(), is(equalTo(LdapAuthProvider.class)));
        assertThat(classProperty.getValueAsSaved(), is("org.jivesoftware.openfire.ldap.LdapAuthProvider"));
    }

    @Test
    public void willNotReturnAnotherClass() {

        final SystemProperty<Class> classProperty = SystemProperty.Builder.ofType(Class.class)
            .setKey("another-subclass-property")
            .setDefaultValue(DefaultAuthProvider.class)
            .setBaseClass(AuthProvider.class)
            .setDynamic(false)
            .build();

        JiveGlobals.setProperty("another-subclass-property", "java.lang.Object");

        assertThat(classProperty.getValue(), is(equalTo(DefaultAuthProvider.class)));
    }

    @Test
    public void willNotReturnAMissingClass() {

        final SystemProperty<Class> classProperty = SystemProperty.Builder.ofType(Class.class)
            .setKey("a-missing-subclass-property")
            .setDefaultValue(DefaultAuthProvider.class)
            .setBaseClass(AuthProvider.class)
            .setDynamic(false)
            .build();

        JiveGlobals.setProperty("a-missing-subclass-property", "this.class.is.missing");

        assertThat(classProperty.getValue(), is(equalTo(DefaultAuthProvider.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildAClassPropertyWithoutABaseClass() {

        SystemProperty.Builder.ofType(Class.class)
            .setKey("a-broken-class-property")
            .setDefaultValue(DefaultAuthProvider.class)
            .setDynamic(false)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void willNotBuildARegularPropertyWithABaseClass() {

        SystemProperty.Builder.ofType(Long.class)
            .setKey("a-broken-long-property")
            .setDefaultValue(42L)
            .setBaseClass(java.lang.Long.class)
            .setDynamic(false)
            .build();
    }

    @Test
    public void shouldEncryptAProperty() {
        final SystemProperty<Long> longProperty = SystemProperty.Builder.ofType(Long.class)
            .setKey("an-encrypted-property")
            .setDefaultValue(42L)
            .setDynamic(false)
            .setEncrypted(true)
            .build();

        longProperty.setValue(84L);

        assertThat(JiveGlobals.isPropertyEncrypted("an-encrypted-property"), is(true));
    }

    @Test
    public void willAllowNullDefaultsForAStringProperty() {

        final SystemProperty<String> stringProperty = SystemProperty.Builder.ofType(String.class)
            .setKey("a-null-string-property")
            .setDynamic(true)
            .build();

        assertThat(stringProperty.getDefaultValue(), is(nullValue()));
    }

    @Test
    public void willAllowNullDefaultsForAClassProperty() {

        final SystemProperty<Class> classProperty = SystemProperty.Builder.ofType(Class.class)
            .setKey("a-null-class-property")
            .setBaseClass(AuthProvider.class)
            .setDynamic(false)
            .build();

        assertThat(classProperty.getDefaultValue(), is(nullValue()));
    }

    @Test
    public void willRemovePluginSpecificProperties() {

        final String key = "a-class-property-to-remove";
        final SystemProperty<Class> property = SystemProperty.Builder.ofType(Class.class)
            .setKey(key)
            .setBaseClass(AuthProvider.class)
            .setPlugin("TestPluginName")
            .setDynamic(false)
            .build();

        assertThat(SystemProperty.getProperty(key), is(Optional.of(property)));
        SystemProperty.removePropertiesForPlugin("TestPluginName");
        assertThat(SystemProperty.getProperty(key), is(Optional.empty()));
    }

    @Test
    public void willCreateAnInstantProperty() {

        final String key = "test.instant.property";
        final SystemProperty<Instant> property = SystemProperty.Builder.ofType(Instant.class)
            .setKey(key)
            .setDynamic(true)
            .build();

        assertThat(property.getValue(), is(nullValue()));
        final Instant value = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        property.setValue(value);
        assertThat(property.getValue(), is(value));
    }

    @Test
    public void willCreateAnInstantPropertyWithADefaultValue() {

        final String key = "test.instant.property.with.default";
        final Instant defaultValue = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        final SystemProperty<Instant> property = SystemProperty.Builder.ofType(Instant.class)
            .setKey(key)
            .setDefaultValue(defaultValue)
            .setDynamic(true)
            .build();

        assertThat(property.getValue(), is(defaultValue));
    }

    @Test
    public void willCreateAListOfStringProperties() {
        final String key = "a list property";

        final SystemProperty<List<String>> property = SystemProperty.Builder.ofType(List.class)
            .setKey(key)
            .setDefaultValue(Collections.emptyList())
            .setDynamic(true)
            .buildList(String.class);

        assertThat(property.getValue(), is(Collections.emptyList()));
        property.setValue(Arrays.asList("3", "2", "1"));
        assertThat(property.getValue(), is(Arrays.asList("3", "2", "1")));
        assertThat(property.getDisplayValue(), is("3,2,1"));
        assertThat(JiveGlobals.getProperty(key), is("3,2,1"));
    }

    @Test
    public void willCreateAListOfLongProperties() {
        final String key = "a list of longs property";

        final SystemProperty<List<Long>> property = SystemProperty.Builder.ofType(List.class)
            .setKey(key)
            .setSorted(true)
            .setDefaultValue(Collections.emptyList())
            .setDynamic(true)
            .buildList(Long.class);

        assertThat(property.getValue(), is(Collections.emptyList()));
        property.setValue(Arrays.asList(3L, 2L, 1L));
        assertThat(property.getValue(), is(Arrays.asList(1L, 2L, 3L)));
        assertThat(property.getDisplayValue(), is("1,2,3"));
        assertThat(JiveGlobals.getProperty(key), is("1,2,3"));
        JiveGlobals.setProperty(key, "3,2,1");
        assertThat(property.getValue(), is(Arrays.asList(1L, 2L, 3L)));
        assertThat(property.getDisplayValue(), is("1,2,3"));
    }

    @Test
    public void willCreateAListOfDurationProperties() {
        final String key = "a list of durations property";

        final SystemProperty<List<Duration>> property = SystemProperty.Builder.ofType(List.class)
            .setChronoUnit(ChronoUnit.HOURS)
            .setKey(key)
            .setDefaultValue(Collections.singletonList(Duration.ZERO))
            .setDynamic(true)
            .buildList(Duration.class);

        assertThat(property.getValue(), is(Collections.singletonList(Duration.ZERO)));
        property.setValue(Arrays.asList(Duration.ofHours(1), Duration.ZERO));
        assertThat(property.getValue(), is(Arrays.asList(Duration.ofHours(1), Duration.ZERO)));
        assertThat(property.getDisplayValue(), is("1 hour,0 ms"));
        assertThat(JiveGlobals.getProperty(key), is("1,0"));
    }

    @Test
    public void willCreateASetOfStringProperties() {
        final String key = "a set property";

        final SystemProperty<Set<String>> property = SystemProperty.Builder.ofType(Set.class)
            .setKey(key)
            .setSorted(true)
            .setDefaultValue(Collections.emptySet())
            .setDynamic(true)
            .buildSet(String.class);

        assertThat(property.getValue(), is(Collections.emptySet()));
        property.setValue(new HashSet<>(Arrays.asList("3", "2", "1", "2")));
        assertThat(property.getValue(), is(new LinkedHashSet<>(Arrays.asList("1", "2", "3"))));
        assertThat(property.getDisplayValue(), is("1,2,3"));
        assertThat(JiveGlobals.getProperty(key), is("1,2,3"));
        JiveGlobals.setProperty(key, "3,2,1,3");
        assertThat(property.getValue(), is(new LinkedHashSet<>(Arrays.asList("1", "2", "3"))));
        assertThat(property.getDisplayValue(), is("1,2,3"));
    }

    @Test
    public void willBuildAJIDProperty() {

        final String jidString = "test-user@test-domain/test-resource";
        final JID jid = new JID(jidString);
        final String key = "a jid property";

        final SystemProperty<JID> property = SystemProperty.Builder.ofType(JID.class)
            .setKey(key)
            .setDynamic(true)
            .build();

        assertThat(property.getValue(),is(nullValue()));
        property.setValue(jid);
        assertThat(property.getValue(),is(jid));
        assertThat(property.getDisplayValue(), is(jidString));
        assertThat(JiveGlobals.getProperty(key), is(jidString));
    }

    @Test
    public void willReturnNullForInvalidJID() {

        final String jidString = "@test-domain";
        final String key = "an invalid jid property";

        final SystemProperty<JID> property = SystemProperty.Builder.ofType(JID.class)
            .setKey(key)
            .setDynamic(true)
            .build();

        assertThat(property.getValue(),is(nullValue()));
        JiveGlobals.setProperty(key, jidString);
        assertThat(property.getValue(),is(nullValue()));
    }

    private enum TestEnum {
        TEST_1,
        TEST_2
    }

    @Test
    public void willBuildAnEnumProperty() {

        final String key = "an enum property";

        final SystemProperty<TestEnum> property = SystemProperty.Builder.ofType(TestEnum.class)
            .setKey(key)
            .setDefaultValue(TestEnum.TEST_1)
            .setDynamic(true)
            .build();

        assertThat(property.getValue(),is(TestEnum.TEST_1));
        property.setValue(TestEnum.TEST_2);
        assertThat(property.getValue(),is(TestEnum.TEST_2));
        assertThat(property.getDisplayValue(), is("TEST_2"));
        assertThat(JiveGlobals.getProperty(key), is("TEST_2"));
    }


}
