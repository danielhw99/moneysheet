FROM eclipse-temurin@sha256:b2711c65970c1227197c29c46e669bfdbb137524e9da0e664703a140ec25a040 AS build
WORKDIR /app
COPY moneysheet/gradlew moneysheet/build.gradle moneysheet/settings.gradle ./
COPY moneysheet/gradle/ ./gradle/
COPY moneysheet/src/ ./src/
COPY moneysheet/deploy/container/ ./deploy/container/
RUN chmod +x gradlew && ./gradlew bootJar migrationDist --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx768m
RUN javac -d build/health deploy/container/HealthProbe.java

FROM eclipse-temurin@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5 AS base
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY moneysheet/deploy/container/entrypoint.sh /opt/entrypoint.sh
RUN chmod 0555 /opt/entrypoint.sh
USER 10001:10001
WORKDIR /opt

FROM base AS migration
COPY --from=build /app/build/migration-dist/ /opt/migration/
ENTRYPOINT ["/opt/entrypoint.sh", "migration"]
CMD ["info"]

FROM base AS app
COPY --from=build /app/build/libs/moneysheet-1.0-SNAPSHOT.jar /opt/moneysheet/app.jar
COPY --from=build /app/build/health/ /opt/health/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=8s --start-period=90s --retries=3 CMD ["java", "-cp", "/opt/health", "HealthProbe"]
ENTRYPOINT ["/opt/entrypoint.sh", "app"]
