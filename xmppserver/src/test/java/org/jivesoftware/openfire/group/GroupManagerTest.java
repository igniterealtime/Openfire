package org.jivesoftware.openfire.group;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.Serializable;
import java.util.Collection;

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;

@RunWith(MockitoJUnitRunner.class)
public class GroupManagerTest {

    private static final String GROUP_NAME = "test-group-name";
    private static Cache<String, CacheableOptional<Group>> groupCache;
    private static Cache<String, Serializable> groupMetadataCache;
    @Mock
    GroupProvider groupProvider;
    private GroupManager groupManager;
    @Mock
    private Group cachedGroup;
    @Mock
    private Group unCachedGroup;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        groupCache = CacheFactory.createCache("Group");
        groupMetadataCache = CacheFactory.createCache("Group Metadata Cache");
        JiveGlobals.setProperty("provider.group.className", TestGroupProvider.class.getName());
    }

    @Before
    public void setUp() {
        groupCache.clear();
        groupMetadataCache.clear();
        TestGroupProvider.mockGroupProvider = groupProvider;
        groupManager = GroupManager.getInstance();
    }

    @Test
    public void willUseACacheHit() throws Exception {

        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        assertThat(returnedGroup, is(cachedGroup));
        verifyNoMoreInteractions(groupProvider);
    }

    @Test
    public void willUseACacheMiss() {

        groupCache.put(GROUP_NAME, CacheableOptional.of(null));

        try {
            groupManager.getGroup(GROUP_NAME, false);
        } catch (final GroupNotFoundException ignored) {
            verifyNoMoreInteractions(groupProvider);
            return;
        }
        fail();
    }

    @Test
    public void willCacheAHitIfNotAlreadyCached() throws Exception {

        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        assertThat(returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    @Test
    public void willCacheAMissIfNotAlreadyCached() throws Exception {

        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        try {
            groupManager.getGroup(GROUP_NAME, false);
        } catch (final GroupNotFoundException ignored) {
            verify(groupProvider).getGroup(GROUP_NAME);
            assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
            return;
        }
        fail();
    }

    @Test
    public void aForceLookupHitWillIgnoreTheExistingCache() throws Exception {
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, true);

        assertThat(returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    @Test
    public void aForceLookupMissWillIgnoreTheExistingCache() throws Exception {
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        try {
            groupManager.getGroup(GROUP_NAME, true);
        } catch (final GroupNotFoundException ignored) {
            verify(groupProvider).getGroup(GROUP_NAME);
            assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
            return;
        }
        fail();
    }

    /**
     * As the GroupManager creates the instance of the GroupProvider, use this class to delegate calls
     * to the mock.
     */
    public static class TestGroupProvider implements GroupProvider {

        private static GroupProvider mockGroupProvider;

        @Override
        public Group createGroup(final String name) throws GroupAlreadyExistsException {
            return mockGroupProvider.createGroup(name);
        }

        @Override
        public void deleteGroup(final String name) {
            mockGroupProvider.deleteGroup(name);
        }

        @Override
        public Group getGroup(final String name) throws GroupNotFoundException {
            return mockGroupProvider.getGroup(name);
        }

        @Override
        public void setName(final String oldName, final String newName) throws GroupAlreadyExistsException {
            mockGroupProvider.setName(oldName, newName);
        }

        @Override
        public void setDescription(final String name, final String description) throws GroupNotFoundException {
            mockGroupProvider.setDescription(name, description);
        }

        @Override
        public int getGroupCount() {
            return mockGroupProvider.getGroupCount();
        }

        @Override
        public Collection<String> getGroupNames() {
            return mockGroupProvider.getGroupNames();
        }

        @Override
        public boolean isSharingSupported() {
            return mockGroupProvider.isSharingSupported();
        }

        @Override
        public Collection<String> getSharedGroupNames() {
            return mockGroupProvider.getSharedGroupNames();
        }

        @Override
        public Collection<String> getSharedGroupNames(final JID user) {
            return mockGroupProvider.getSharedGroupNames(user);
        }

        @Override
        public Collection<String> getPublicSharedGroupNames() {
            return mockGroupProvider.getPublicSharedGroupNames();
        }

        @Override
        public Collection<String> getVisibleGroupNames(final String userGroup) {
            return mockGroupProvider.getVisibleGroupNames(userGroup);
        }

        @Override
        public Collection<String> getGroupNames(final int startIndex, final int numResults) {
            return mockGroupProvider.getGroupNames(startIndex, numResults);
        }

        @Override
        public Collection<String> getGroupNames(final JID user) {
            return mockGroupProvider.getGroupNames(user);
        }

        @Override
        public void addMember(final String groupName, final JID user, final boolean administrator) {
            mockGroupProvider.addMember(groupName, user, administrator);
        }

        @Override
        public void updateMember(final String groupName, final JID user, final boolean administrator) {
            mockGroupProvider.updateMember(groupName, user, administrator);
        }

        @Override
        public void deleteMember(final String groupName, final JID user) {
            mockGroupProvider.deleteMember(groupName, user);
        }

        @Override
        public boolean isReadOnly() {
            return mockGroupProvider.isReadOnly();
        }

        @Override
        public Collection<String> search(final String query) {
            return mockGroupProvider.search(query);
        }

        @Override
        public Collection<String> search(final String query, final int startIndex, final int numResults) {
            return mockGroupProvider.search(query, startIndex, numResults);
        }

        @Override
        public Collection<String> search(final String key, final String value) {
            return mockGroupProvider.search(key, value);
        }

        @Override
        public boolean isSearchSupported() {
            return mockGroupProvider.isSearchSupported();
        }

        @Override
        public PersistableMap<String, String> loadProperties(final Group group) {
            return mockGroupProvider.loadProperties(group);
        }
    }
}
