FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /workspace/chayitong-backend

COPY chayitong-backend/pom.xml ./
COPY chayitong-backend/.mvn .mvn
COPY chayitong-backend/mvnw chayitong-backend/mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY chayitong-backend/src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /workspace/chayitong-backend/target/app.jar /app/app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
