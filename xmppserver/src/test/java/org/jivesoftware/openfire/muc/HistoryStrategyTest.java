/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc;

import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link HistoryStrategy}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class HistoryStrategyTest
{
    /**
     * Asserts that when a populated instance of HistoryStrategy is serialized and the resulting data deserialized again, an
     * instance results that is equal to the original input.
     */
    @Test
    public void testExternalizedEquals() throws Exception
    {
        // Setup test fixture.
        final HistoryStrategy input = dummyHistoryStrategy();
        populateField(input, "parent", dummyHistoryStrategy()); // Use another instance as the parent for our input instance.

        // Execute system under test.
        final byte[] serialized;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
            oos.writeObject(input);
            serialized = baos.toByteArray();
        }

        final Object result;
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             final ObjectInputStream ois = new ObjectInputStream(bais)) {
            result = ois.readObject();
        }

        // Verify results.
        assertNotNull(result);
        assertTrue(result instanceof HistoryStrategy);
        assertEquals(input, result);
        assertEquals(input.getType(), ((HistoryStrategy) result).getType());
        assertEquals(input.getMaxNumber(), ((HistoryStrategy) result).getMaxNumber());
        assertNotNull(input.getChangedSubject());
        assertNotNull(((HistoryStrategy) result).getChangedSubject());
        assertEquals(input.getChangedSubject().toXML(), ((HistoryStrategy) result).getChangedSubject().toXML());

        final AbstractList<String> inputMessageTextHistory = new ArrayList<>();
        final ListIterator<Message> inputReverseMessageHistory = input.getReverseMessageHistory();
        while(inputReverseMessageHistory.hasNext()) {
            inputMessageTextHistory.add(inputReverseMessageHistory.next().toXML());
        }

        final AbstractList<String> resultMessageTextHistory = new ArrayList<>();
        final ListIterator<Message> resultReverseMessageHistory = input.getReverseMessageHistory();
        while(resultReverseMessageHistory.hasNext()) {
            resultMessageTextHistory.add(resultReverseMessageHistory.next().toXML());
        }

        assertEquals(inputMessageTextHistory, resultMessageTextHistory);
    }

    public static <E> void populateField(final E object, final String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        try {
            field.set(object, value);
        } finally {
            field.setAccessible(false);
        }
    }

    /**
     * Returns a randomly populated HistoryStrategy instance (that has no parent).
     *
     * @return a HistoryStrategy instance.
     */
    public static HistoryStrategy dummyHistoryStrategy() throws NoSuchFieldException, IllegalAccessException
    {
        final Message h1 = new Message();
        h1.setFrom(new JID("test" + StringUtils.randomString(4) + "@example.org"));
        h1.setTo(new JID("foo" + StringUtils.randomString(4) + "@example.org"));
        h1.setType(Message.Type.groupchat);
        h1.setBody("This is a historic message that is used in a unit test. Some random value to make text unique: " + StringUtils.randomString(10) );
        h1.addChildElement("delay", "urn:xmpp:delay").addAttribute("stamp", "2");

        final Message h2 = new Message();
        h2.setFrom(new JID("bar" + StringUtils.randomString(4) + "@example.org"));
        h2.setTo(new JID("foobar" +StringUtils.randomString(4)+ "@example.org"));
        h2.setType(Message.Type.groupchat);
        h2.setBody("This is another historic message that is used in a unit test. Some random value to make text unique: " + StringUtils.randomString(10));
        h2.addChildElement("delay", "urn:xmpp:delay").addAttribute("stamp", "1");

        final ConcurrentLinkedQueue<Message> history = new ConcurrentLinkedQueue<>();
        history.add(h1);
        history.add(h2);

        final Message subject = new Message();
        subject.setFrom(new JID("bar" + StringUtils.randomString(4) + "@example.org"));
        subject.setTo(new JID("foobar" +StringUtils.randomString(4)+ "@example.org"));
        subject.setType(Message.Type.groupchat);
        subject.setBody("This is a subject message that is used in a unit test. Some random value to make text unique: " + StringUtils.randomString(10));
        subject.addChildElement("delay", "urn:xmpp:delay").addAttribute("stamp", "4");

        final HistoryStrategy result = new HistoryStrategy(); // Set all fields to a non-default value, for a more specific test!
        populateField(result, "type", HistoryStrategy.Type.all);
        populateField(result, "roomJID", new JID("test" + StringUtils.randomString(4) + "@example.org"));
        populateField(result, "maxNumber", new Random().nextInt(10000));
        populateField(result, "parent", null);
        populateField(result, "roomSubject", subject);
        populateField(result, "contextPrefix", "test prefix");
        populateField(result, "contextSubdomain", "test subdomain");
        history.forEach(result::addMessage);

        return result;
    }
}
