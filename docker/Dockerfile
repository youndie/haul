# The server's image, with an AOT cache trained on the JRE that runs it (research §1.5, risk 4).
#
# The context is the installed distribution — `./gradlew :server:installDist`, then this file with
# `server/build/install/server` as the context; `scripts/image-check.sh` does both.
#
# **Why a training stage with its own PostgreSQL.** The JVM accepts a cache only from the very build
# that wrote it, down to the size of `lib/modules`, so the cache is trained on this image's JRE and
# nowhere else. Training starts the server, the server will not start without a database, and an image
# build has none — so this stage installs one, trains against it, and is thrown away; the final stage
# starts again from the same base and takes only the application.

ARG TEMURIN=25.0.4_7

FROM eclipse-temurin:${TEMURIN}-jre AS train
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql brotli \
 && rm -rf /var/lib/apt/lists/*
COPY . /opt/app
# **The browser bundle, precompressed once here rather than on every request** (B-34): a `.br`
# (quality 11) and a `.gz` (-9) beside each file the page loads, which the server picks by the
# browser's Accept-Encoding (`WebBundle.kt`). Here and not in Gradle because this is where an encoder
# is: brotli has no pure-JVM encoder, and a build machine cannot be counted on for `brotli`; this stage is
# thrown away, so the encoder never reaches the image. About half a minute, mostly skiko's 8 MB wasm.
RUN find /opt/app/web -type f \( -name '*.wasm' -o -name '*.js' -o -name '*.mjs' -o -name '*.html' \
      -o -name '*.css' -o -name '*.json' -o -name '*.ttf' -o -name '*.otf' -o -name '*.svg' \) -print0 \
    | xargs -0 -r -P 4 -I{} sh -c 'brotli -q 11 -f -o "$1.br" "$1" && gzip -9 -n -k -f "$1"' _ {}
ENV HAUL_DB_URL=jdbc:postgresql://127.0.0.1:5432/haul \
    HAUL_DB_USER=haul \
    HAUL_DB_PASSWORD=haul \
    HAUL_SEED=true \
    HAUL_WEB_DIR=/opt/app/web
# `train` runs the start script with -XX:AOTCacheOutput, waits for /readyz, runs the workload of
# server/build.gradle.kts and stops the JVM; `verify` fails the build unless the JVM accepts the cache.
# The seed is part of what is trained: a start that seeds is a start that loads Exposed and Flyway.
# The last step makes everything readable by the user the final stage runs as: a distribution copied
# from a checkout with private file modes (a synced replica has them) is otherwise a page that answers
# 500 to uid 1000 behind green probes.
RUN set -eu; \
    PG=$(ls -d /usr/lib/postgresql/*/bin); \
    mkdir -p /tmp/pg && chown postgres /tmp/pg; \
    su postgres -c "$PG/initdb -D /tmp/pg/data -A trust -U postgres >/dev/null"; \
    su postgres -c "$PG/pg_ctl -D /tmp/pg/data -o '-k /tmp/pg' -w start >/dev/null"; \
    su postgres -c "psql -h /tmp/pg -c \"CREATE USER haul PASSWORD 'haul'\" -c 'CREATE DATABASE haul OWNER haul'"; \
    java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main train /opt/app; \
    java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main verify /opt/app; \
    su postgres -c "$PG/pg_ctl -D /tmp/pg/data -m fast -w stop >/dev/null"; \
    rm -f /opt/app/lib/zavarnik-*.log; \
    chmod -R a+rX /opt/app

FROM eclipse-temurin:${TEMURIN}-jre
# Links the published package to the repository, whose visibility it then takes (B-27).
LABEL org.opencontainers.image.source="https://github.com/youndie/haul"
COPY --from=train /opt/app /opt/app
# The browser bundle the distribution carries (`server/build.gradle.kts`), served at `/`.
ENV HAUL_WEB_DIR=/opt/app/web
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["/opt/app/bin/server"]
