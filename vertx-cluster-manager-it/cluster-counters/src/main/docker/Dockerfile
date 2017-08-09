FROM openjdk:8-jre-alpine

ENV JAVA_APP_JAR cluster-counters-1.0-SNAPSHOT.jar

ENV JAVA_OPTIONS "-Djava.net.preferIPv4Stack=true -Dvertx.jgroups.config=default-configs/default-jgroups-kubernetes.xml"
ENV KUBERNETES_NAMESPACE myproject
ENV KUBERNETES_LABELS "cluster=cluster-counters"


EXPOSE 8080

ADD maven/$JAVA_APP_JAR /app/
RUN chmod 777 /app/

WORKDIR /app/
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTIONS -jar $JAVA_APP_JAR -cluster"]
