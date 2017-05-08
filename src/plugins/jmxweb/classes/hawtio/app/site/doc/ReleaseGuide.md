## hawtio release guide

The following walks through how we make a release.

* Pop onto [IRC](http://hawt.io/community/index.html) and let folks know you're about to cut a release
* Now pull and make sure things build locally fine first :)

		mvn release:prepare -P release,grunt

If the build fails then rollback via

    mvn release:rollback -P release,grunt

The tag should get auto-defaulted to something like **hawtio-1.2**

		mvn release:perform -P release,grunt

when the release is done:

		git push --tags

Now go to the [OSS Nonatype Nexus](https://oss.sonatype.org/index.html#stagingRepositories) and Close then Release the staging repo

Now update the new dev version the following files so the new dev build doens't barf

  * [SpecRunner.html](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/test/specs/SpecRunner.html#L88)
  * website/pom.xml

Update the *.md files to point to the new version (being careful not to break various configuration documents and the changes.md which list specific versions).

Update the extension.xml file to point to the new version.

Now, go into github issues and create a new milestone (if not already created) for the release number that you just released.  Close this milestone.  Now go through each open milestonee and move all closed issues to your new milestone.  Also move issues that are closed but have no milestone to the new milestone.  This will ensure that all fixed issues in the last development period will be correctly associated with the release that the fix was introduced in.

Update the changelog with links to your milestone which will list all the fixes/enhancements that made it into the release.  Also mention any major changes in the changelog.

Now drink a beer! Then another! There, thats better now isn't it!




