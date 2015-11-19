package com.jivesoftware.tools.selector;

public class IncludeSourceSelector
  extends BaseSelector
{
  protected String getTagName()
  {
    return "src.include";
  }
  
  protected boolean shouldCopyWithNoTag()
  {
    return true;
  }
  
  protected boolean shouldCopy(String paramString)
  {
    return !"false".equals(paramString);
  }
}
