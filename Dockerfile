FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:copy-dependencies

COPY src ./src
RUN mkdir -p target/classes \
    && javac --release 25 -cp "target/dependency/*" -d target/classes $(find src/main/java -name '*.java')

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./libs

EXPOSE 8080

ENV PORT=8080

CMD ["sh", "-c", "java -cp /app/classes:/app/libs/* com.oraskin.App ${PORT}"]
