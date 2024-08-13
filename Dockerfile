# This stage extracts all the pom.xml files.
# It'll get rebuilt with any source change, but that's OK.
FROM eclipse-temurin:17 AS poms
WORKDIR /usr/src
COPY . .
# Wipe any files not called pom.xml or *.jar
RUN find . -type f -and \! -name pom.xml -and \! -name '*.jar' -delete
# Clear up any (now) empty diretories
RUN find . -type d -empty -delete
# Just for debug:
RUN find

# Now we build:
FROM openjdk:11-jdk AS build
# Set up Maven. No need to clean caches, this doesn't end up in the runtime container
RUN apt-get update -qq
RUN apt-get install -qqy maven
WORKDIR /tmp/
RUN mkdir /tmp/m2_home
ENV M2_HOME=/tmp/m2_home
WORKDIR /usr/src

# First, copy in just the pom.xml files and fetch the dependencies:
COPY --from=poms /usr/src/ .
RUN mvn -e -B dependency:go-offline
# Above here is only affected by the pom.xml files, so the cache is stable.

# Now, copy in all the source, and actually build it, skipping the tests.
COPY . .
RUN mvn -e -B package -Dmaven.test.skip

# Final stage, build the runtime container:
FROM eclipse-temurin:17

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire

RUN apt-get update -qq \
    && apt-get install -yqq sudo \
    && adduser --disabled-password --quiet --system --home $OPENFIRE_DATA_DIR --gecos "Openfire XMPP server" --group $OPENFIRE_USER \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=0755 --from=build /usr/src/build/docker/entrypoint.sh /sbin/entrypoint.sh
COPY --chown=openfire:openfire --from=build /usr/src/distribution/target/distribution-base /usr/local/openfire
RUN mv ${OPENFIRE_DIR}/conf ${OPENFIRE_DIR}/conf_org \
    && mv ${OPENFIRE_DIR}/plugins ${OPENFIRE_DIR}/plugins_org \
    && mv ${OPENFIRE_DIR}/resources/security ${OPENFIRE_DIR}/resources/security_org

LABEL maintainer="florian.kinder@fankserver.com"
WORKDIR /usr/local/openfire

EXPOSE 3478 3479 5005 5222 5223 5229 5262 5263 5275 5276 7070 7443 7777 9090 9091
VOLUME ["${OPENFIRE_DATA_DIR}"]
VOLUME ["${OPENFIRE_LOG_DIR}"]
ENTRYPOINT [ "/sbin/entrypoint.sh" ]
