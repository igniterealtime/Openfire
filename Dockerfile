FROM openjdk:8-jdk
COPY . .
RUN apt-get update -qq \
    && apt-get install -qqy ant \
    && ant -Dhalt.on.plugin.error=true -Dno.package=true -f build/build.xml dist.bin
FROM openjdk:8-jre
COPY --from=0 /target/release/openfire /usr/local/openfire
WORKDIR /usr/local/openfire
#RUN adduser --disabled-login --uid 999 --home /usr/local/openfire --gecos "" openfire \
#    && chown -R openfire:openfire /usr/local/openfire
#USER openfire
VOLUME ["/usr/local/openfire/conf", "/usr/local/openfire/embedded-db", "/usr/local/openfire/plugins", "/usr/local/openfire/resources/security"]
CMD ["/usr/local/openfire/bin/openfire.sh"]
