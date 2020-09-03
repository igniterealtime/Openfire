# syntax=docker/dockerfile:1.0.0-experimental
FROM amazon/aws-cli as aws
# download push notification credentials from S3
RUN --mount=type=secret,id=aws,target=/root/.aws/credentials aws s3 cp s3://com.feinfone.build/apns/apns_key.p8 /usr/local/openfire/authKey.p8

# TODO probably pass build arguments with docker-compose
ARG VERSION_DBACCESS=1.2.2
ARG VERSION_REGISTRATION=1.7.2
ARG VERSION_RESTAPI=1.4.0
ARG VERSION_SUBSCRIPTION=1.4.0

# Maven target to get all dependencies
FROM maven:3.6.2-jdk-11 as packager
WORKDIR /usr/src

COPY ./pom.xml .
COPY ./i18n/pom.xml ./i18n/
COPY ./xmppserver/pom.xml ./xmppserver/
COPY ./starter/pom.xml ./starter/
COPY ./starter/libs/* ./starter/libs/
COPY ./plugins/pom.xml ./plugins/
COPY ./plugins/openfire-plugin-assembly-descriptor/pom.xml ./plugins/openfire-plugin-assembly-descriptor/
COPY ./distribution/pom.xml ./distribution/

# get all necessary plugins
# 1. official plugins
# DB Access (Official Openfire plugin)
RUN wget https://www.igniterealtime.org/projects/openfire/plugins/${VERSION_DBACCESS}/dbaccess.jar -O ./plugins/dbaccess.jar \
# Registration (Official Openfire plugin)
 && wget https://www.igniterealtime.org/projects/openfire/plugins/${VERSION_REGISTRATION}/registration.jar -O ./plugins/registration.jar \
# REST API (Official Openfire plugin)
 && wget https://www.igniterealtime.org/projects/openfire/plugins/${VERSION_RESTAPI}/restAPI.jar -O ./plugins/restAPI.jar \
# Subscription (Official Openfire plugin)
 && wget https://www.igniterealtime.org/projects/openfire/plugins/${VERSION_SUBSCRIPTION}/subscription.jar -O ./plugins/subscription.jar

# 2. our plugins: clone private repository and use host machines SSH key
# Download public key for github.com
RUN mkdir -p -m 0600 ~/.ssh && ssh-keyscan github.com >> ~/.ssh/known_hosts
# [Avatar upload plugin](https://github.com/voiceup-chat/openfire-avatar-upload-plugin)
RUN --mount=type=ssh git clone git@github.com:voiceup-chat/openfire-avatar-upload-plugin.git ./plugins/openfire-avatar-upload-plugin \
# [Hazelcast plugin](https://github.com/nsobadzhiev/openfire-hazelcast-plugin)
 && git clone git@github.com:voiceup-chat/openfire-voice-plugin.git ./plugins/openfire-voice-plugin \
# [Voice Upload](https://github.com/voiceup-chat/openfire-voice-plugin)
 && git clone git@github.com:voiceup-chat/openfire-apns.git ./plugins/openfire-apns \
# [Feinfone APNS](https://github.com/voiceup-chat/openfire-apns)
 && git clone git@github.com:nsobadzhiev/openfire-hazelcast-plugin.git ./plugins/openfire-hazelcast-plugin

RUN mvn dependency:go-offline

COPY . .
RUN mvn package

# build target
FROM openjdk:11-jre-slim as build
COPY --from=packager /usr/src/distribution/target/distribution-base /usr/local/openfire
COPY --from=packager /usr/src/build/docker/entrypoint.sh /sbin/entrypoint.sh
WORKDIR /usr/local/openfire

COPY build/docker/inject_db_settings.sh \
     build/docker/inject_hazelcast_settings.sh \
     build/docker/template_openfire.xml \
     build/docker/template_hazelcast.xml \
     build/docker/template_security.xml \
     ./

# copy push notification credentials
COPY --from=aws /usr/local/openfire/authKey.p8 .

# (move all plugin JARs to the plugin folder)
COPY --from=packager /usr/src/plugins/openfire-avatar-upload-plugin/target/avatarupload-0.0.1-SNAPSHOT.jar \
     /usr/src/plugins/openfire-voice-plugin/target/voice-0.0.11-SNAPSHOT.jar \
     /usr/src/plugins/openfire-apns/target/openfire-apns.jar \
     /usr/src/plugins/openfire-hazelcast-plugin/target/hazelcast-2.4.2-SNAPSHOT.jar \
     ./

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

EXPOSE 3478/tcp 3479/tcp 5222/tcp 5223/tcp 5229/tcp 5275/tcp 5276/tcp 5262/tcp 5263/tcp 5701/tcp 7070/tcp 7443/tcp 7777/tcp 9090/tcp 9091/tcp
VOLUME ["${OPENFIRE_DATA_DIR}"]
CMD ["/sbin/entrypoint.sh"]
