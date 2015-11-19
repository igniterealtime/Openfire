package com.jivesoftware.tools.selector;

public class JavaDocSelector
  extends BaseSelector
{
  public static final String VERSION_API = "api";
  public static final String VERSION_DEV = "dev";
  public static final String VERSION_NONE = "none";
  String version = "dev";
  
  protected String getTagName()
  {
    return "javadoc";
  }
  
  protected boolean shouldCopyWithNoTag()
  {
    return "dev".equals(this.version);
  }
  
  protected boolean shouldCopy(String paramString)
  {
    if ("none".equals(paramString)) {
      return false;
    }
    if ("dev".equals(this.version)) {
      return true;
    }
    if ("api".equals(this.version)) {
      return "api".equals(paramString);
    }
    setError("Unknown selector: " + this.version);
    return false;
  }
  
  public void setVersion(String paramString)
  {
    this.version = paramString;
  }
}
