FROM anapsix/alpine-java:8_server-jre_unlimited

VOLUME /tmp

ADD app.jar app.jar
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]

