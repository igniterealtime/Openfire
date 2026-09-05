/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link ResumeRequest}, which parses a XEP-0198 {@code <resume/>} element, be it a traditional top-level
 * one, or one nested inline inside a SASL2 {@code <authenticate/>} element.
 */
public class ResumeRequestTest
{
    /**
     * Verifies that a valid, traditional, top-level {@code <resume/>} element is parsed correctly.
     */
    @Test
    public void testFromTraditionalResumeElement() throws Exception
    {
        // Setup test fixture.
        final Element resume = DocumentHelper.createElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "5");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.from(resume);

        // Verify result.
        assertEquals("cHJldmlk", result.getPrevId(), "Expected the parsed previd value to match the value in the 'previd' attribute.");
        assertEquals(5L, result.getH(), "Expected the parsed h value to match the value in the 'h' attribute.");
        assertEquals(StreamManager.NAMESPACE_V3, result.getNamespace(), "Expected the parsed namespace to match the namespace used by the 'resume' element.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when a traditional, top-level
     * {@code <resume/>} element is missing its required {@code h} attribute.
     */
    @Test
    public void testFromTraditionalResumeElementMissingH() throws Exception
    {
        // Setup test fixture.
        final Element resume = DocumentHelper.createElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.from(resume), "Expected a MalformedResumeRequestException to be thrown, as the 'resume' element is missing its required 'h' attribute.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when a traditional, top-level
     * {@code <resume/>} element is missing its required {@code previd} attribute.
     */
    @Test
    public void testFromTraditionalResumeElementMissingPrevid()
    {
        // Setup test fixture.
        final Element resume = DocumentHelper.createElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("h", "5");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.from(resume), "Expected a MalformedResumeRequestException to be thrown, as the 'resume' element is missing its required 'previd' attribute.");
    }

    /**
     * Verifies that {@code null} is returned when the {@code <authenticate/>} element does not contain a
     * {@code <resume/>} child element at all.
     */
    @Test
    public void testFromElementWithoutResumeElement() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.fromSasl2Authenticate(authenticate);

        // Verify result.
        assertNull(result, "Expected no ResumeRequest to be returned, as the input has no 'resume' child element.");
    }

    /**
     * Verifies that {@code null} is returned when the {@code <resume/>} child element uses a namespace other than
     * one of the recognized XEP-0198 namespaces.
     */
    @Test
    public void testFromElementWithWrongNamespace() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(QName.get("resume", "wrong:namespace"));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "5");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.fromSasl2Authenticate(authenticate);

        // Verify result.
        assertNull(result, "Expected no ResumeRequest to be returned, as the 'resume' element uses an unrecognized namespace.");
    }

    /**
     * Verifies that a valid {@code <resume/>} element using the XEP-0198 v3 namespace is parsed correctly.
     */
    @Test
    public void testFromValidResumeElementV3() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "5");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.fromSasl2Authenticate(authenticate);

        // Verify result.
        assertNotNull(result, "Expected a ResumeRequest to be returned, as the input contains a valid 'resume' element.");
        assertEquals("cHJldmlk", result.getPrevId(), "Expected the parsed previd value to match the value in the 'previd' attribute.");
        assertEquals(5L, result.getH(), "Expected the parsed h value to match the value in the 'h' attribute.");
        assertEquals(StreamManager.NAMESPACE_V3, result.getNamespace(), "Expected the parsed namespace to match the namespace used by the 'resume' element.");
    }

    /**
     * Verifies that a valid {@code <resume/>} element using the XEP-0198 v2 namespace is parsed correctly.
     */
    @Test
    public void testFromValidResumeElementV2() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V2)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "0");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.fromSasl2Authenticate(authenticate);

        // Verify result.
        assertNotNull(result, "Expected a ResumeRequest to be returned, as the input contains a valid 'resume' element.");
        assertEquals("cHJldmlk", result.getPrevId(), "Expected the parsed previd value to match the value in the 'previd' attribute.");
        assertEquals(0L, result.getH(), "Expected the parsed h value to match the value in the 'h' attribute.");
        assertEquals(StreamManager.NAMESPACE_V2, result.getNamespace(), "Expected the parsed namespace to match the namespace used by the 'resume' element.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the
     * {@code <resume/>} element is missing its required {@code previd} attribute.
     */
    @Test
    public void testFromElementMissingPrevid() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("h", "5");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'resume' element is missing its required 'previd' attribute.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the
     * {@code <resume/>} element is missing its required {@code h} attribute.
     */
    @Test
    public void testFromElementMissingH() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'resume' element is missing its required 'h' attribute.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the
     * {@code h} attribute of the {@code <resume/>} element does not contain a valid number.
     */
    @Test
    public void testFromElementMalformedH() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "not-a-number");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'h' attribute is not a valid number.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the
     * {@code h} attribute of the {@code <resume/>} element is a negative number.
     */
    @Test
    public void testFromElementNegativeH() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "-1");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'h' attribute is negative.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the
     * {@code previd} attribute of the {@code <resume/>} element is empty.
     */
    @Test
    public void testFromElementEmptyPrevid() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "");
        resume.addAttribute("h", "5");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'previd' attribute is empty.");
    }

    /**
     * Verifies that the largest legal value for the {@code h} attribute (XEP-0198 § 4 defines it as an unsigned
     * 32-bit integer) is accepted.
     */
    @Test
    public void testFromElementMaximumH() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "4294967295");

        // Execute system under test.
        final ResumeRequest result = ResumeRequest.fromSasl2Authenticate(authenticate);

        // Verify result.
        assertNotNull(result, "Expected a ResumeRequest to be returned, as the input contains a valid 'resume' element.");
        assertEquals(4294967295L, result.getH(), "Expected the parsed h value to match the value in the 'h' attribute.");
    }

    /**
     * Verifies that a {@link MalformedResumeRequestException} is thrown when the {@code h} attribute of the
     * {@code <resume/>} element exceeds the largest legal value for an unsigned 32-bit integer.
     */
    @Test
    public void testFromElementExcessiveH() throws Exception
    {
        // Setup test fixture.
        final Element authenticate = DocumentHelper.createElement("authenticate");
        final Element resume = authenticate.addElement(new QName("resume", new Namespace("", StreamManager.NAMESPACE_V3)));
        resume.addAttribute("previd", "cHJldmlk");
        resume.addAttribute("h", "4294967296");

        // Execute system under test & verify result.
        assertThrows(MalformedResumeRequestException.class, () -> ResumeRequest.fromSasl2Authenticate(authenticate), "Expected a MalformedResumeRequestException to be thrown, as the 'h' attribute exceeds the largest legal value.");
    }
}
