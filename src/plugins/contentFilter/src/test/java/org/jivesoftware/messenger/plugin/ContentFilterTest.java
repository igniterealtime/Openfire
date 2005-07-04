package org.jivesoftware.messenger.plugin;

import java.util.regex.PatternSyntaxException;

import junit.framework.TestCase;

import org.xmpp.packet.Message;

/**
 * Basic unit tests for ContentFilter.
 * 
 * @author chayes
 */
public class ContentFilterTest extends TestCase
{
  private ContentFilter filter;

  public static void main(String[] args)
  {
    junit.textui.TestRunner.run(ContentFilterTest.class);
  }

  /*
   * @see TestCase#setUp()
   */
  protected void setUp() throws Exception
  {
    filter = new ContentFilter();
  }

  /*
   * @see TestCase#tearDown()
   */
  protected void tearDown() throws Exception
  {
    super.tearDown();
  }

  public void testSetInvalidPatterns()
  {
    try
    {
      filter.setPatterns("$*[");
    }
    catch (PatternSyntaxException e)
    {
      System.out.println(e.getMessage());
      System.out.println(e.getPattern());
    }
  }
  public void testSetContentMask()
  {
    assertFalse(filter.isMaskingContent());
    filter.setMask("dummy");
    assertTrue(filter.isMaskingContent());
    filter.clearMask();
    assertFalse(filter.isMaskingContent());
  }

  public void testFilterWithEmptyMessage()
  {
    Message message = new Message();
    boolean matched = filter.filter(message);

    // no matches should be found
    assertFalse(matched);

    // message should not have changed
    assertEquals(new Message().toXML(), message.toXML());
  }

  public void testFilterMessageSubject()
  {
    // filter on the word fox
    filter.setPatterns("fox");

    // test message
    Message message = new Message();
    message.setSubject("the quick brown fox jumped over the lazy dog");
    boolean matched = filter.filter(message);

    // matches should be found
    assertTrue(matched);

    // content has not changed as there is no content mask
    assertEquals("the quick brown fox jumped over the lazy dog", message
        .getSubject());
    assertNull(message.getBody());
  }

  public void testFilterMessageSubjectWithMask()
  {

    // filter on the word fox
    filter.setPatterns("fox");
    
    //set a content mask 
    filter.setMask("**");

    // test message
    Message message = new Message();
    message.setSubject("the quick brown fox jumped over the lazy dog");
    boolean matched = filter.filter(message);

    // matches should be found
    assertTrue(matched);

    // content has changed
    assertEquals("the quick brown ** jumped over the lazy dog", message
        .getSubject());
    assertNull(message.getBody());

  }

  public void testFilterMessageBody()
  {

    // filter on the word fox
    filter.setPatterns("fox");

    // test message
    Message message = new Message();
    message.setBody("the quick brown fox jumped over the lazy dog");
    boolean matched = filter.filter(message);

    // matches should be found
    assertTrue(matched);

    // content has not changed as there is no content mask
    assertEquals("the quick brown fox jumped over the lazy dog", message
        .getBody());
    assertNull(message.getSubject());
  }

  public void testFilterMessageBodyWithMask()
  {

    // filter on the word "fox" and "dog"
    filter.setPatterns("fox,dog");
    filter.setMask("**");

    // test message
    Message message = new Message();
    message.setBody("the quick brown fox jumped over the lazy dog");
    boolean matched = filter.filter(message);

    // matches should not be found
    assertTrue(matched);

    // content has changed
    assertEquals("the quick brown ** jumped over the lazy **", message
        .getBody());
    assertNull(message.getSubject());

  }
}