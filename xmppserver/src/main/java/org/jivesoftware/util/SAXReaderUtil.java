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
package org.jivesoftware.util;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegate;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegateMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * A utility class that provides a level of abstraction around the creation of a SAXReader instance suitable for XMPP
 * parsing, as well as maintaining a set of readers ready, available for (bulk) processing.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public final class SAXReaderUtil
{
    // Note: this combines a ExecutorService with ThreadLocal usage, which introduces the potential to introduce memory
    //       leaks. The tasks executed by the service make use of a thread-local parser, to avoid the overhead of having
    //       to recreate these. When threads are terminated (after inactivity), this parser cannot be cleaned up (the
    //       API of ThreadPoolExecutor does not provide this functionality). This means that a reference to the value of
    //       the ThreadLocal (the parser) will retain a reference to its ClassLoader, which prevents garbage collection.
    //       As the class of the instance of the parser (SAXReader.class) is loaded by the core classloader in Openfire,
    //       this should only prevent garbage collection of a ClassLoader that should only be garbage collected when
    //       Openfire is shutting down. However, this structure MUST NOT be used using for a value a Class that's loaded
    //       by a plugin. The reference to the PluginClassLoader won't be cleaned, which prevents garbage collection of
    //       the plugin, which prevents the plugin from properly unloading (eg, when it is updated/restarted), which
    //       over time will lead to a wide variety of hard-to-diagnose issues.

    private static final Logger Log = LoggerFactory.getLogger(SAXReaderUtil.class);

    /**
     * The number of threads to keep in the SAX Reader pool, even if they are idle.
     */
    public static final SystemProperty<Integer> PARSER_SERVICE_CORE_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
                                                                                    .setKey("xmpp.xmlutil.parser.core-pool-size")
                                                                                    .setMinValue(0)
                                                                                    .setDefaultValue(0)
                                                                                    .setDynamic(false)
                                                                                    .build();

    /**
     * The maximum number of threads to allow in the SAX Reader pool.
     */
    public static final SystemProperty<Integer> PARSER_SERVICE_MAX_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
                                                                                    .setKey("xmpp.xmlutil.parser.maximum-pool-size")
                                                                                    .setMinValue(1)
                                                                                    .setDefaultValue(25)
                                                                                    .setDynamic(false)
                                                                                    .build();

    /**
     * When the number of threads in the SAX reader pool is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
     */
    public static final SystemProperty<Duration> PARSER_SERVICE_KEEP_ALIVE_TIME = SystemProperty.Builder.ofType(Duration.class)
                                                                                    .setKey("xmpp.xmlutil.parser.keep_alive_time")
                                                                                    .setChronoUnit(ChronoUnit.SECONDS)
                                                                                    .setMinValue(Duration.ofMillis(0))
                                                                                    .setDefaultValue(Duration.ofMinutes(1))
                                                                                    .setDynamic(false)
                                                                                    .build();

    private static final ThreadPoolExecutor parserService = new ThreadPoolExecutor(
                                                            PARSER_SERVICE_CORE_POOL_SIZE.getValue(),
                                                            PARSER_SERVICE_MAX_POOL_SIZE.getValue(),
                                                            PARSER_SERVICE_KEEP_ALIVE_TIME.getValue().toMillis(), TimeUnit.MILLISECONDS,
                                                            new SynchronousQueue<>(),
                                                            new NamedThreadFactory("saxReaderUtil-", Executors.defaultThreadFactory(), false, Thread.NORM_PRIORITY));

    static
    {
        if (JMXManager.isEnabled()) {
            final ThreadPoolExecutorDelegateMBean mBean = new ThreadPoolExecutorDelegate(parserService);
            JMXManager.tryRegister(mBean, ThreadPoolExecutorDelegateMBean.BASE_OBJECT_NAME + "saxReaderUtil");
        }
    }

    /**
     * Schedules parsing of the provided input stream into an XML Document as an asynchronous process. This method
     * returns a {@link Future} object that can be used to obtain the result of the asynchronous process.
     *
     * The XML parsing is delegated to a limited set of parsers, that is fronted by an Executor Service. The amount of
     * parsers that are available is equal to the amount of threads available in the Executor Service. This can be
     * optimized by using the SystemProperties in this class.
     *
     * @param stream The data to be parsed as an XML Document.
     * @return A Future representing pending completion of parsing the provided data as an XML Document.
     */
    public static Future<Document> readDocumentAsync(@Nonnull final InputStream stream) {
        return parserService.submit(new ParserTask(stream));
    }

    /**
     * Parses an XML Document from the content provided by an input stream.
     *
     * Equivalent to calling <tt>readDocumentAsync(input).get()</tt>
     *
     * The XML parsing is delegated to a limited set of parsers, as documented in {@link #readDocumentAsync(InputStream)}.
     * When a parser is not immediately available, this method will block until one becomes available.
     *
     * @param stream The data to be parsed as an XML Document.
     * @return an XML Document.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Document readDocument(@Nonnull final InputStream stream) throws ExecutionException, InterruptedException {
        return readDocumentAsync(stream).get();
    }

    /**
     * Parses an XML Document from the content provided by an input stream and returns the root element of the document.
     *
     * Equivalent to calling <tt>readDocument(input).getRootElement()</tt>
     *
     * @param stream The data to be parsed as an XML Document.
     * @return an XML Document root element.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Element readRootElement(@Nonnull final InputStream stream) throws ExecutionException, InterruptedException {
        return readDocument(stream).getRootElement();
    }

    /**
     * Schedules parsing of the provided text into an XML Document as an asynchronous process. This method
     * returns a {@link Future} object that can be used to obtain the result of the asynchronous process.
     *
     * The XML parsing is delegated to a limited set of parsers, that is fronted by an Executor Service. The amount of
     * parsers that are available is equal to the amount of threads available in the Executor Service. This can be
     * optimized by using the SystemProperties in this class.
     *
     * @param text The data to be parsed as an XML Document.
     * @return A Future representing pending completion of parsing the provided data as an XML Document.
     */
    public static Future<Document> readDocumentAsync(@Nonnull final String text) {
        return parserService.submit(new ParserTask(new StringReader(text)));
    }

    /**
     * Parses an XML Document from the provided text.
     *
     * Equivalent to calling <tt>readDocumentAsync(input).get()</tt>
     *
     * The XML parsing is delegated to a limited set of parsers, as documented in {@link #readDocumentAsync(String)}.
     * When a parser is not immediately available, this method will block until one becomes available.
     *
     * @param text The data to be parsed as an XML Document.
     * @return an XML Document.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Document readDocument(@Nonnull final String text) throws ExecutionException, InterruptedException {
        return readDocumentAsync(text).get();
    }

    /**
     * Parses an XML Document from the provided text and returns the root element of the document.
     *
     * Equivalent to calling <tt>readDocument(input).getRootElement()</tt>
     *
     * @param text The data to be parsed as an XML Document.
     * @return an XML Document root element.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Element readRootElement(@Nonnull final String text) throws ExecutionException, InterruptedException {
        return readDocument(text).getRootElement();
    }

    /**
     * Schedules parsing of the provided reader into an XML Document as an asynchronous process. This method
     * returns a {@link Future} object that can be used to obtain the result of the asynchronous process.
     *
     * The XML parsing is delegated to a limited set of parsers, that is fronted by an Executor Service. The amount of
     * parsers that are available is equal to the amount of threads available in the Executor Service. This can be
     * optimized by using the SystemProperties in this class.
     *
     * @param reader The data to be parsed as an XML Document.
     * @return A Future representing pending completion of parsing the provided data as an XML Document.
     */
    public static Future<Document> readDocumentAsync(@Nonnull final Reader reader) {
        return parserService.submit(new ParserTask(reader));
    }

    /**
     * Parses an XML Document from the content provided by a reader.
     *
     * Equivalent to calling <tt>readDocumentAsync(input).get()</tt>
     *
     * The XML parsing is delegated to a limited set of parsers, as documented in {@link #readDocumentAsync(Reader)}.
     * When a parser is not immediately available, this method will block until one becomes available.
     *
     * @param reader The data to be parsed as an XML Document.
     * @return an XML Document.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Document readDocument(@Nonnull final Reader reader) throws ExecutionException, InterruptedException {
        return readDocumentAsync(reader).get();
    }

    /**
     * Parses an XML Document from the content provided by a reader and returns the root element of the document.
     *
     * Equivalent to calling <tt>readDocument(input).getRootElement()</tt>
     *
     * @param reader The data to be parsed as an XML Document.
     * @return an XML Document root element.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Element readRootElement(@Nonnull final Reader reader) throws ExecutionException, InterruptedException {
        return readDocument(reader).getRootElement();
    }

    /**
     * Schedules parsing of content of afile into an XML Document as an asynchronous process. This method
     * returns a {@link Future} object that can be used to obtain the result of the asynchronous process.
     *
     * The XML parsing is delegated to a limited set of parsers, that is fronted by an Executor Service. The amount of
     * parsers that are available is equal to the amount of threads available in the Executor Service. This can be
     * optimized by using the SystemProperties in this class.
     *
     * @param file The data to be parsed as an XML Document.
     * @return A Future representing pending completion of parsing the provided data as an XML Document.
     */
    public static Future<Document> readDocumentAsync(@Nonnull final File file) {
        return parserService.submit(new ParserTask(file));
    }

    /**
     * Parses an XML Document from the content provided by a file.
     *
     * Equivalent to calling <tt>readDocumentAsync(input).get()</tt>
     *
     * The XML parsing is delegated to a limited set of parsers, as documented in {@link #readDocumentAsync(File)}.
     * When a parser is not immediately available, this method will block until one becomes available.
     *
     * @param file The data to be parsed as an XML Document.
     * @return an XML Document.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Document readDocument(@Nonnull final File file) throws ExecutionException, InterruptedException {
        return readDocumentAsync(file).get();
    }

    /**
     * Parses an XML Document from the content provided by a file and returns the root element of the document.
     *
     * Equivalent to calling <tt>readDocument(input).getRootElement()</tt>
     *
     * @param file The data to be parsed as an XML Document.
     * @return an XML Document root element.
     * @throws ExecutionException on any error that occurs during parsing (this typically has a {@link org.dom4j.DocumentException} as its cause).
     * @throws InterruptedException when the task that is scheduled to perform the parsing gets interrupted during the execution of the task.
     */
    public static Element readRootElement(@Nonnull final File file) throws ExecutionException, InterruptedException {
        return readDocument(file).getRootElement();
    }

    /**
     * Constructs a new SAXReader instance, configured for XMPP parsing.
     *
     * @return A new SAXReader instance.
     */
    private static SAXReader constructNewReader() throws SAXException
    {
        final SAXReader saxReader = new SAXReader();
        saxReader.setEncoding( "UTF-8" );
        saxReader.setIgnoreComments(true);

        // Guard against various known vulnerabilities (see OF-2094).
        saxReader.setEntityResolver((publicId, systemId) -> {
            throw new IOException("External entity denied: " + publicId + " // " + systemId);
        });
        saxReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        saxReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        saxReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        return saxReader;
    }

    private static class ParserTask implements Callable<Document>
    {
        private static final ThreadLocal<SAXReader> localSAXReader = ThreadLocal.withInitial(()-> {
            try {
                return constructNewReader();
            } catch (SAXException e) {
                Log.error("Unable to construct a new XML parser.", e);
                return null;
            }
        });

        @Nullable
        private final InputStream stream;

        @Nullable
        private final Reader reader;

        @Nullable
        private final File file;

        public ParserTask(@Nonnull final InputStream stream) {
            this.stream = stream;
            this.reader = null;
            this.file = null;
        }

        public ParserTask(@Nonnull final Reader reader) {
            this.stream = null;
            this.reader = reader;
            this.file = null;
        }

        public ParserTask(@Nonnull final File file) {
            this.stream = null;
            this.reader = null;
            this.file = file;
        }

        @Override
        public Document call() throws Exception {
            if (stream != null) {
                return localSAXReader.get().read(stream);
            } else if (reader != null) {
                return localSAXReader.get().read(reader);
            } else if (file != null) {
                return localSAXReader.get().read(file);
            } else {
                // Did you forget to add an 'else' block for the new type that you added?
                throw new IllegalStateException("Unable to parse data. Data is either null, or of an unrecognized type.");
            }
        }
    }

//    // This allows for a quick and dirty verification that the SAXReader instances that are values of the ThreadLocal
//    // are indeed garbage collected after the threads expire. To verify, use jmap to dump a histogram that shows the
//    // number of instances directly after starting the application, and after the first 'sleep' has expired (and all
//    // threads should have been terminated by the Service).
//    public static void main(String[] args) throws ExecutionException, InterruptedException {
//        for (int i = 0; i<300; i++) {
//            XmlUtils.readDocument(new java.io.StringReader("<foo></foo>"));
//        }
//        System.out.println("Histo should now have up to 25 SAXReader instances.");
//        Thread.sleep(65_000); // wait for threads to time out
//        System.gc();
//        System.out.println("Histo should now have 0 SAXReader instances.");
//        Thread.sleep(65_000);
//    }
}
