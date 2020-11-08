FROM maven:3.6.3-jdk-8 as packager
WORKDIR /usr/src

COPY ./pom.xml .
COPY ./i18n/pom.xml ./i18n/
COPY ./xmppserver/pom.xml ./xmppserver/
COPY ./starter/pom.xml ./starter/
COPY ./starter/libs/* ./starter/libs/
COPY ./plugins/pom.xml ./plugins/
COPY ./plugins/openfire-plugin-assembly-descriptor/pom.xml ./plugins/openfire-plugin-assembly-descriptor/
COPY ./distribution/pom.xml ./distribution/
RUN mvn dependency:go-offline

COPY . .
RUN mvn package

FROM openjdk:8-jre
COPY --from=packager /usr/src/distribution/target/distribution-base /usr/local/openfire
COPY --from=packager /usr/src/build/docker/entrypoint.sh /sbin/entrypoint.sh
WORKDIR /usr/local/openfire

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire

RUN apt-get update -qq \
    && apt-get install -yqq sudo \
    && adduser --disabled-password --quiet --system --home $OPENFIRE_DATA_DIR --gecos "Openfire XMPP server" --group openfire \
    && chmod 755 /sbin/entrypoint.sh \
    && chown -R openfire:openfire ${OPENFIRE_DIR} \
    && mv ${OPENFIRE_DIR}/conf ${OPENFIRE_DIR}/conf_org \
    && mv ${OPENFIRE_DIR}/plugins ${OPENFIRE_DIR}/plugins_org \
    && mv ${OPENFIRE_DIR}/resources/security ${OPENFIRE_DIR}/resources/security_org \
    && rm -rf /var/lib/apt/lists/*

LABEL maintainer="florian.kinder@fankserver.com"

EXPOSE 3478/tcp 3479/tcp 5222/tcp 5223/tcp 5229/tcp 5275/tcp 5276/tcp 5262/tcp 5263/tcp 7070/tcp 7443/tcp 7777/tcp 9090/tcp 9091/tcp
VOLUME ["${OPENFIRE_DATA_DIR}"]
CMD ["/sbin/entrypoint.sh"]
