/**
 * $RCSfile: $
 * $Revision: 3135 $
 * $Date: 2005-12-01 02:03:04 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.Reader;

/**
 * MXParser that returns an IGNORABLE_WHITESPACE event when a whitespace character or a
 * line feed is received. This parser is useful when not validating documents.
 *
 * @author Gaston Dombiak
 */
public class MXParser extends org.xmlpull.mxp1.MXParser {

    /**
     * Last time a heartbeat was received. Hearbeats are represented as whitespaces
     * or \n characters received when an XmlPullParser.END_TAG was parsed. Note that we
     * can falsely detect heartbeats when parsing XHTML content but that is fine.
     */
    private long lastHeartbeat = 0;

    protected int nextImpl()
        throws XmlPullParserException, IOException
    {
        text = null;
        pcEnd = pcStart = 0;
        usePC = false;
        bufStart = posEnd;
        if(pastEndTag) {
            pastEndTag = false;
            --depth;
            namespaceEnd = elNamespaceCount[ depth ]; // less namespaces available
        }
        if(emptyElementTag) {
            emptyElementTag = false;
            pastEndTag = true;
            return eventType = END_TAG;
        }

        // [1] document ::= prolog element Misc*
        if(depth > 0) {

            if(seenStartTag) {
                seenStartTag = false;
                return eventType = parseStartTag();
            }
            if(seenEndTag) {
                seenEndTag = false;
                return eventType = parseEndTag();
            }

            // ASSUMPTION: we are _on_ first character of content or markup!!!!
            // [43] content ::= CharData? ((element | Reference | CDSect | PI | Comment) CharData?)*
            char ch;
            if(seenMarkup) {  // we have read ahead ...
                seenMarkup = false;
                ch = '<';
            } else if(seenAmpersand) {
                seenAmpersand = false;
                ch = '&';
            } else {
                ch = more();
            }
            posStart = pos - 1; // VERY IMPORTANT: this is correct start of event!!!

            // when true there is some potential event TEXT to return - keep gathering
            boolean hadCharData = false;

            // when true TEXT data is not continous (like <![CDATA[text]]>) and requires PC merging
            boolean needsMerging = false;

            MAIN_LOOP:
            while(true) {
                // work on MARKUP
                if(ch == '<') {
                    if(hadCharData) {
                        //posEnd = pos - 1;
                        if(tokenize) {
                            seenMarkup = true;
                            return eventType = TEXT;
                        }
                    }
                    ch = more();
                    if(ch == '/') {
                        if(!tokenize && hadCharData) {
                            seenEndTag = true;
                            //posEnd = pos - 2;
                            return eventType = TEXT;
                        }
                        return eventType = parseEndTag();
                    } else if(ch == '!') {
                        ch = more();
                        if(ch == '-') {
                            // note: if(tokenize == false) posStart/End is NOT changed!!!!
                            parseComment();
                            if(tokenize) return eventType = COMMENT;
                            if( !usePC && hadCharData ) {
                                needsMerging = true;
                            } else {
                                posStart = pos;  //completely ignore comment
                            }
                        } else if(ch == '[') {
                            //posEnd = pos - 3;
                            // must remeber previous posStart/End as it merges with content of CDATA
                            //int oldStart = posStart + bufAbsoluteStart;
                            //int oldEnd = posEnd + bufAbsoluteStart;
                            parseCDSect(hadCharData);
                            if(tokenize) return eventType = CDSECT;
                            final int cdStart = posStart;
                            final int cdEnd = posEnd;
                            final int cdLen = cdEnd - cdStart;


                            if(cdLen > 0) { // was there anything inside CDATA section?
                                hadCharData = true;
                                if(!usePC) {
                                    needsMerging = true;
                                }
                            }

                            //                          posStart = oldStart;
                            //                          posEnd = oldEnd;
                            //                          if(cdLen > 0) { // was there anything inside CDATA section?
                            //                              if(hadCharData) {
                            //                                  // do merging if there was anything in CDSect!!!!
                            //                                  //                                    if(!usePC) {
                            //                                  //                                        // posEnd is correct already!!!
                            //                                  //                                        if(posEnd > posStart) {
                            //                                  //                                            joinPC();
                            //                                  //                                        } else {
                            //                                  //                                            usePC = true;
                            //                                  //                                            pcStart = pcEnd = 0;
                            //                                  //                                        }
                            //                                  //                                    }
                            //                                  //                                    if(pcEnd + cdLen >= pc.length) ensurePC(pcEnd + cdLen);
                            //                                  //                                    // copy [cdStart..cdEnd) into PC
                            //                                  //                                    System.arraycopy(buf, cdStart, pc, pcEnd, cdLen);
                            //                                  //                                    pcEnd += cdLen;
                            //                                  if(!usePC) {
                            //                                      needsMerging = true;
                            //                                      posStart = cdStart;
                            //                                      posEnd = cdEnd;
                            //                                  }
                            //                              } else {
                            //                                  if(!usePC) {
                            //                                      needsMerging = true;
                            //                                      posStart = cdStart;
                            //                                      posEnd = cdEnd;
                            //                                      hadCharData = true;
                            //                                  }
                            //                              }
                            //                              //hadCharData = true;
                            //                          } else {
                            //                              if( !usePC && hadCharData ) {
                            //                                  needsMerging = true;
                            //                              }
                            //                          }
                        } else {
                            throw new XmlPullParserException(
                                "unexpected character in markup "+printable(ch), this, null);
                        }
                    } else if(ch == '?') {
                        parsePI();
                        if(tokenize) return eventType = PROCESSING_INSTRUCTION;
                        if( !usePC && hadCharData ) {
                            needsMerging = true;
                        } else {
                            posStart = pos;  //completely ignore PI
                        }

                    } else if( isNameStartChar(ch) ) {
                        if(!tokenize && hadCharData) {
                            seenStartTag = true;
                            //posEnd = pos - 2;
                            return eventType = TEXT;
                        }
                        return eventType = parseStartTag();
                    } else {
                        throw new XmlPullParserException(
                            "unexpected character in markup "+printable(ch), this, null);
                    }
                    // do content comapctation if it makes sense!!!!

                } else if(ch == '&') {
                    // work on ENTITTY
                    //posEnd = pos - 1;
                    if(tokenize && hadCharData) {
                        seenAmpersand = true;
                        return eventType = TEXT;
                    }
                    final int oldStart = posStart + bufAbsoluteStart;
                    final int oldEnd = posEnd + bufAbsoluteStart;
                    final char[] resolvedEntity = parseEntityRef();
                    if(tokenize) return eventType = ENTITY_REF;
                    // check if replacement text can be resolved !!!
                    if(resolvedEntity == null) {
                        if(entityRefName == null) {
                            entityRefName = newString(buf, posStart, posEnd - posStart);
                        }
                        throw new XmlPullParserException(
                            "could not resolve entity named '"+printable(entityRefName)+"'",
                            this, null);
                    }
                    //int entStart = posStart;
                    //int entEnd = posEnd;
                    posStart = oldStart - bufAbsoluteStart;
                    posEnd = oldEnd - bufAbsoluteStart;
                    if(!usePC) {
                        if(hadCharData) {
                            joinPC(); // posEnd is already set correctly!!!
                            needsMerging = false;
                        } else {
                            usePC = true;
                            pcStart = pcEnd = 0;
                        }
                    }
                    //assert usePC == true;
                    // write into PC replacement text - do merge for replacement text!!!!
                    for (int i = 0; i < resolvedEntity.length; i++)
                    {
                        if(pcEnd >= pc.length) ensurePC(pcEnd);
                        pc[pcEnd++] = resolvedEntity[ i ];

                    }
                    hadCharData = true;
                    //assert needsMerging == false;
                } else {

                    if(needsMerging) {
                        //assert usePC == false;
                        joinPC();  // posEnd is already set correctly!!!
                        //posStart = pos  -  1;
                        needsMerging = false;
                    }


                    //no MARKUP not ENTITIES so work on character data ...



                    // [14] CharData ::=   [^<&]* - ([^<&]* ']]>' [^<&]*)


                    hadCharData = true;

                    boolean normalizedCR = false;
                    final boolean normalizeInput = tokenize == false || roundtripSupported == false;
                    // use loop locality here!!!!
                    boolean seenBracket = false;
                    boolean seenBracketBracket = false;
                    do {

                        // check that ]]> does not show in
                        if (eventType == XmlPullParser.END_TAG &&
                                (ch == ' ' || ch == '\n' || ch == '\t')) {
                            // ** ADDED CODE (INCLUDING IF STATEMENT)
                            lastHeartbeat = System.currentTimeMillis();;
                        }
                        if(ch == ']') {
                            if(seenBracket) {
                                seenBracketBracket = true;
                            } else {
                                seenBracket = true;
                            }
                        } else if(seenBracketBracket && ch == '>') {
                            throw new XmlPullParserException(
                                "characters ]]> are not allowed in content", this, null);
                        } else {
                            if(seenBracket) {
                                seenBracketBracket = seenBracket = false;
                            }
                            // assert seenTwoBrackets == seenBracket == false;
                        }
                        if(normalizeInput) {
                            // deal with normalization issues ...
                            if(ch == '\r') {
                                normalizedCR = true;
                                posEnd = pos -1;
                                // posEnd is alreadys set
                                if(!usePC) {
                                    if(posEnd > posStart) {
                                        joinPC();
                                    } else {
                                        usePC = true;
                                        pcStart = pcEnd = 0;
                                    }
                                }
                                //assert usePC == true;
                                if(pcEnd >= pc.length) ensurePC(pcEnd);
                                pc[pcEnd++] = '\n';
                            } else if(ch == '\n') {
                                //   if(!usePC) {  joinPC(); } else { if(pcEnd >= pc.length) ensurePC(); }
                                if(!normalizedCR && usePC) {
                                    if(pcEnd >= pc.length) ensurePC(pcEnd);
                                    pc[pcEnd++] = '\n';
                                }
                                normalizedCR = false;
                            } else {
                                if(usePC) {
                                    if(pcEnd >= pc.length) ensurePC(pcEnd);
                                    pc[pcEnd++] = ch;
                                }
                                normalizedCR = false;
                            }
                        }

                        ch = more();
                    } while(ch != '<' && ch != '&');
                    posEnd = pos - 1;
                    continue MAIN_LOOP;  // skip ch = more() from below - we are alreayd ahead ...
                }
                ch = more();
            } // endless while(true)
        } else {
            if(seenRoot) {
                return parseEpilog();
            } else {
                return parseProlog();
            }
        }
    }

    /**
     * Returns the last time a heartbeat was received. Hearbeats are represented as whitespaces
     * or \n characters received when an XmlPullParser.END_TAG was parsed. Note that we
     * can falsely detect heartbeats when parsing XHTML content but that is fine.
     *
     * @return the time in milliseconds when a heartbeat was received.
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void resetInput() {
        Reader oldReader = reader;
        String oldEncoding = inputEncoding;
        reset();
        reader = oldReader;
        inputEncoding = oldEncoding;
    }
}
