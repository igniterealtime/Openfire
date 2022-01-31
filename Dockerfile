FROM openjdk:11-jre

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire

RUN apt-get update -qq \
    && apt-get install -yqq sudo \
    && adduser --disabled-password --quiet --system --home $OPENFIRE_DATA_DIR --gecos "Openfire XMPP server" --group $OPENFIRE_USER \
    && rm -rf /var/lib/apt/lists/*

COPY ./build/docker/entrypoint.sh /sbin/entrypoint.sh
RUN chmod 755 /sbin/entrypoint.sh

COPY --chown=openfire:openfire ./distribution/target/distribution-base /usr/local/openfire
RUN mv ${OPENFIRE_DIR}/conf ${OPENFIRE_DIR}/conf_org \
    && mv ${OPENFIRE_DIR}/plugins ${OPENFIRE_DIR}/plugins_org \
    && mv ${OPENFIRE_DIR}/resources/security ${OPENFIRE_DIR}/resources/security_org

LABEL maintainer="florian.kinder@fankserver.com"
WORKDIR /usr/local/openfire

EXPOSE 3478 3479 5005 5222 5223 5229 5262 5263 5275 5276 7070 7443 7777 9090 9091
VOLUME ["${OPENFIRE_DATA_DIR}"]
ENTRYPOINT [ "/sbin/entrypoint.sh" ]
