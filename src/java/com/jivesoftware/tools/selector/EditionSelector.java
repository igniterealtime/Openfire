package com.jivesoftware.tools.selector;

public class EditionSelector
  extends BaseSelector
{
  public static final String EDITION_PRO = "professional";
  public static final String EDITION_SILVER = "silver";
  public static final String EDITION_GOLD = "gold";
  String edition = "gold";
  
  protected String getTagName()
  {
    return "edition";
  }
  
  protected boolean shouldCopyWithNoTag()
  {
    return true;
  }
  
  protected boolean shouldCopy(String paramString)
  {
    if ("gold".equals(this.edition)) {
      return true;
    }
    if ("silver".equals(this.edition)) {
      return !"gold".equals(paramString);
    }
    if ("professional".equals(this.edition))
    {
      if ("gold".equals(paramString)) {
        return false;
      }
      return !"silver".equals(paramString);
    }
    setError("Unknown selector: " + this.edition);
    return false;
  }
  
  public void setEdition(String paramString)
  {
    this.edition = paramString;
  }
}
