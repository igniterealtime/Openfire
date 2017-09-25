FROM openjdk:8-jdk
COPY . .
RUN apt-get update -qq \
    && apt-get install -qqy ant \
    && ant -Dhalt.on.plugin.error=true -Dno.package=true -f build/build.xml dist.bin
FROM openjdk:8-jre
#RUN adduser -D -u 999 openfire
COPY --from=0 /target/release/openfire /usr/local/openfire
VOLUME ["/usr/local/openfire/conf", "/usr/local/openfire/embedded-db", "/usr/local/openfire/plugins", "/usr/local/openfire/resources/security"]
CMD ["/usr/local/openfire/bin/openfire.sh"]
