This folder contains an (old) version of the install 4j runtime JAR file. Current ones are available via Maven (see below)
however they do not include some of the classes that are required by org.jivesoftware.openfire.launcher.Uninstaller

    <repositories>
        <repository>
            <id>ej-technologies</id>
            <url>https://maven.ej-technologies.com/repository</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.install4j</groupId>
            <artifactId>install4j-runtime</artifactId>
            <version>7.0.7</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

