FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY target/spring-k8s-leader-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
