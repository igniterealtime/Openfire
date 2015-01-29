### IDE

The IDE plugin makes it easy to be able to link to a source file on a hawtio view so that it can opened for editing in your IDE. Currently supported IDEs are:

* [IDEA](http://www.jetbrains.com/idea/)

To use the directive just include the following markup on a page...

    <hawtio-open-ide file-name="Foo.java" class-name="com.acme.Foo" line="20" column="3"></hawtio-open-ide>

The class-name attribute is often included in log files and stack traces; often the file name has no directory part; so the directive combines as best it can the path information from the package with the file name; then uses the IdeFacadeMBean's [findClassAbsoluteFileName() method](https://github.com/hawtio/hawtio/blob/master/hawtio-ide/src/main/java/io/hawt/ide/IdeFacadeMBean.java#L14-14) to find the absolute file locally on disk so that it can be opened up in IDEA.

Then you can enable/disable whichever IDEs you like to use; so the right links/buttons appear for you (or you can hide them).