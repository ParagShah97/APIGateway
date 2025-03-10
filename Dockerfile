FROM openjdk:21

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} cloudgateway.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/cloudgateway.jar"]

