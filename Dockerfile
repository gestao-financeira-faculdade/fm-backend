FROM amazoncorretto:25 AS build
RUN dnf install -y tar gzip && dnf clean all
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B package -DskipTests \
 && cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM amazoncorretto:25-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
ENV JAVA_BASE_OPTS="-Xms64m -Xmx256m -XX:+UseSerialGC -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED"
RUN java $JAVA_BASE_OPTS -XX:AOTCacheOutput=app.aot \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jooq.sql-dialect=postgres \
      -Dapp.security.jwt.secret=aot-training-only-secret-not-used-at-runtime \
      -jar application.jar
USER app
ENV JAVA_OPTS="-XX:MaxMetaspaceSize=200m -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_BASE_OPTS -XX:AOTCache=app.aot $JAVA_OPTS -jar application.jar"]
