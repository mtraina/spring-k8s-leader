FROM eclipse-temurin:11-jre

WORKDIR /app
COPY target/leader-demo-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
