package com.jivesoftware.tools.selector;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.ClassLibrary;
import com.thoughtworks.qdox.model.DefaultDocletTagFactory;
import com.thoughtworks.qdox.model.DocletTag;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.selectors.BaseSelectorContainer;

public abstract class BaseSelector
  extends BaseSelectorContainer
{
  private static Map sourcesMap = new HashMap();
  private static Map packagesMap = new HashMap();
  private String packages;
  
  public boolean isSelected(File paramFile1, String paramString, File paramFile2)
    throws BuildException
  {
    if (paramString.endsWith(".java"))
    {
      JavaSource[] arrayOfJavaSource = getSources(paramFile1);
      for (int i = 0; i < arrayOfJavaSource.length; i++)
      {
        JavaSource localJavaSource = arrayOfJavaSource[i];
        File localFile = new File(localJavaSource.getURL().getFile());
        if ((localFile.equals(paramFile2)) && (localJavaSource.getClasses().length > 0))
        {
          JavaClass localJavaClass = localJavaSource.getClasses()[0];
          String str = getTagName();
          DocletTag localDocletTag = localJavaClass.getTagByName(str);
          if (localDocletTag == null) {
            return handleNoTag(localJavaClass, paramFile2);
          }
          System.out.println("Checking '" + str + "' tag for " + paramString);
          return shouldCopy(localDocletTag.getValue());
        }
      }
    }
    else if ("javadocs.properties".equals(paramFile2.getName()))
    {
      return false;
    }
    return true;
  }
  
  private boolean handleNoTag(JavaClass paramJavaClass, File paramFile)
  {
    File localFile1 = paramFile.getParentFile();
    String str1 = paramJavaClass.getPackage();
    int i = str1.split("\\.").length;
    for (int j = 0; j < i; j++)
    {
      File localFile2 = new File(localFile1, "javadocs.properties");
      if (localFile2.exists())
      {
        Properties localProperties = new Properties();
        try
        {
          localProperties.load(new FileInputStream(localFile2));
        }
        catch (IOException localIOException)
        {
          localIOException.printStackTrace();
        }
        String str2 = getTagName();
        String str3 = localProperties.getProperty(str2);
        if (str3 != null) {
          return shouldCopy(str3);
        }
      }
      localFile1 = localFile1.getParentFile();
    }
    return shouldCopyWithNoTag();
  }
  
  protected JavaSource[] getSources(File paramFile)
  {
    JavaSource[] arrayOfJavaSource = (JavaSource[])sourcesMap.get(paramFile.toString());
    if (arrayOfJavaSource == null)
    {
      JavaDocBuilder localJavaDocBuilder = new JavaDocBuilder(new DefaultDocletTagFactory());
      localJavaDocBuilder.getClassLibrary().addClassLoader(getClass().getClassLoader());
      localJavaDocBuilder.addSourceTree(paramFile);
      arrayOfJavaSource = localJavaDocBuilder.getSources();
      sourcesMap.put(paramFile.toString(), arrayOfJavaSource);
    }
    return arrayOfJavaSource;
  }
  
  public void setPackages(String paramString)
  {
    this.packages = paramString;
  }
  
  protected abstract String getTagName();
  
  protected abstract boolean shouldCopyWithNoTag();
  
  protected abstract boolean shouldCopy(String paramString);
}
