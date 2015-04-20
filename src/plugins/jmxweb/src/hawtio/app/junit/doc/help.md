### JUnit

The [JUnit](#/junit/tests/) plugin in [hawtio](http://hawt.io "hawtio") offers functionality for viewing/running JUnit test cases.

##### JUnit Tree #####

The topmost level of the JUnit tree view lists all the JUnit @Test classed currently within the running JVM.

![JUnit Tree](app/junit/doc/img/junit-tree.png "JUnit Tree")

Click the <i class='icon-chevron-right'></i> icon to expand a tree item and further navigate into Java packages.


##### Testing #####

On the main view area is a table that lists all the unit test classes from the selected Java package in the JUnit Tree (will list all test classes by default).

![JUnit Tests](app/junit/doc/img/junit-tests.png "JUnit Tests")

You can select any number of unit test classes to run as unit tests.

During testing there is a test summary which reports the progress.

![JUnit Testing](app/junit/doc/img/junit-testing.png "JUnit Testing")

The test summary dialog can be closed by clicking the little close &times; button on the top right corner.


##### Failed Tests #####

If any tests failed then the test summary is shown with red background and a list of the failed tests is shown

![JUnit Failed Tests](app/junit/doc/img/junit-error.png "JUnit Failed Tests")

... which can be expanded by clicking the bullet on the failed test, to see the stacktrace with more details.

![JUnit Failed Stacktrace](app/junit/doc/img/junit-error-expanded.png "JUnit Failed Stacktrace")

And in the stracktrace clicking the IDE icon will load the file in the editor (if its currently running).
*Notice:* At this time of writing only Intellij IDEA is supported.


##### Other Functionality #####

The [JUnit](#/junit/) can be used together with the [Test Maven Plugin](http://hawt.io/maven/) which can be used to startup
the hawtio console of a given Maven project, with unit test classes included in the classpath, allowing the [JUnit](#/junit/)
to be used to start the tests, while using the rest of the functionality of hawtio to inspect live data while the test runs.
