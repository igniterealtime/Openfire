# This stage extracts all the pom.xml files.
# It'll get rebuilt with any source change, but that's OK.
# It doesn't matter what image we're using, really, so we may as well use one of the same images as elsewhere.
FROM eclipse-temurin:17-jre AS poms
WORKDIR /usr/src
COPY . .
# Wipe any files not called pom.xml or *.jar
RUN find . -type f -and \! -name pom.xml -and \! -name '*.jar' -delete
# Clear up any (now) empty diretories
RUN find . -type d -empty -delete

# Now we build:
FROM eclipse-temurin:17 AS build
WORKDIR /tmp/
WORKDIR /usr/src
COPY mvnw ./
RUN chmod +x mvnw
RUN mkdir -p .mvn
COPY .mvn/wrapper .mvn/wrapper

# First, copy in the pom.xml files (and any checked-in jars) and fetch the dependencies into a real image layer.
# This layer depends on the POMs, the checked-in jars, and the Maven wrapper (copied above), so it stays cached
# until one of those changes; dependency downloads are reused whenever the source changes but those inputs don't.
COPY --from=poms /usr/src/ .
# I don't know why we need all three either.
RUN ./mvnw -e -B dependency:resolve-plugins -Dmaven.test.skip -Dmaven.repo.local=/tmp/m2_repo
RUN ./mvnw -e -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies -Dmaven.repo.local=/tmp/m2_repo
# The go-offline plugin and dependency:resolve-plugins do not reliably resolve BOM POMs referenced
# via <scope>import</scope> in <dependencyManagement>. Fetch them explicitly so they are present
# in the repository before the offline build runs. Add any newly introduced BOMs here in the same way.
RUN ./mvnw -e -B dependency:get -DgroupId=org.codehaus.plexus -DartifactId=plexus-utils -Dpackaging=jar -Dversion=1.1 -Dmaven.repo.local=/tmp/m2_repo && \
    ./mvnw -e -B dependency:get -DgroupId=org.junit -DartifactId=junit-bom -Dpackaging=pom -Dversion=6.1.0 -Dmaven.repo.local=/tmp/m2_repo && \
    ./mvnw -e -B dependency:get -DgroupId=org.mockito -DartifactId=mockito-bom -Dpackaging=pom -Dversion=5.23.0 -Dmaven.repo.local=/tmp/m2_repo

# Above here is affected only by the POMs, checked-in jars, and the Maven wrapper, so the layer is usually stable.

# Now, copy in all the source, and actually build it, skipping the tests.
# The offline build reads the /tmp/m2_repo that the layers above populated.
COPY . .
RUN ./mvnw -o -e -B install -Dmaven.test.skip -Dmaven.repo.local=/tmp/m2_repo
# In case of Windows, break glass.
RUN sed -i 's/\r//g' /usr/src/distribution/target/distribution-base/bin/openfire.sh

# Might as well create the user in a different stage if only to eliminate
# the ugly && chaining and increase parallelization
FROM eclipse-temurin:17-jre AS skeleton-runtime

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire

RUN apt-get update -qq
RUN apt-get install -yyq adduser
RUN adduser --disabled-password --quiet --system --home $OPENFIRE_DATA_DIR --gecos "Openfire XMPP server" --group $OPENFIRE_USER

# Final stage, build the runtime container:
FROM eclipse-temurin:17-jre AS runtime

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire

COPY --from=skeleton-runtime /etc/passwd /etc/shadow /etc/group /etc/
COPY --chown=$OPENFIRE_USER:$OPENFIRE_USER --from=skeleton-runtime $OPENFIRE_DATA_DIR $OPENFIRE_DATA_DIR
COPY --chmod=0755 --from=build /usr/src/build/docker/entrypoint.sh /sbin/entrypoint.sh
COPY --chown=$OPENFIRE_USER:$OPENFIRE_USER --from=build /usr/src/distribution/target/distribution-base /usr/local/openfire
RUN mv ${OPENFIRE_DIR}/conf ${OPENFIRE_DIR}/conf_org \
    && mv ${OPENFIRE_DIR}/plugins ${OPENFIRE_DIR}/plugins_org \
    && mv ${OPENFIRE_DIR}/resources/security ${OPENFIRE_DIR}/resources/security_org

LABEL org.opencontainers.image.authors="dave@cridland.net,dan@caseley.me.uk"
WORKDIR /usr/local/openfire
HEALTHCHECK --interval=1m --timeout=10s --start-period=3m --retries=3  CMD bash -c "(echo > /dev/tcp/localhost/5222) 2>/dev/null || exit 1"

EXPOSE 3478 3479 5005 5222 5223 5229 5262 5263 5275 5276 7070 7443 7777 9090 9091
VOLUME ["${OPENFIRE_DATA_DIR}"]
VOLUME ["${OPENFIRE_LOG_DIR}"]
ENTRYPOINT [ "/sbin/entrypoint.sh" ]
