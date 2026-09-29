# syntax=docker/dockerfile:1

# ---- build: compile with the project's own Maven wrapper, then split the jar into cacheable layers ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
# Dependencies change far less often than code, so resolve them in a layer of their own.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in CI against H2 and MySQL; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
	&& cp target/oneday-*.jar application.jar \
	&& java -Djarmode=tools -jar application.jar extract --layers --destination /layers

# ---- runtime: JRE plus ffmpeg, which re-encodes videos and strips their metadata before anyone sees them ----
FROM eclipse-temurin:25-jre
RUN apt-get update \
	&& apt-get install -y --no-install-recommends ffmpeg \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system oneday \
	&& useradd --system --gid oneday --home-dir /app --shell /usr/sbin/nologin oneday
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER oneday
# Size the heap from the container limit; restart cleanly instead of limping on after an OOM. Netty (under
# the Redis client) loads native code, which Java 25 only allows quietly when it is enabled explicitly.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
