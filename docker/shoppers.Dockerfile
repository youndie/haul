# The synthetic shoppers (B-31): the e2e module's `main`, which walks the storefront's whole path on a
# stand continuously so it has orders and sagas to measure. A plain JRE and the distribution — no AOT
# cache: it starts once per deploy and spends its life waiting on the fulfilment clock.
#
# The context is the installed distribution — `./gradlew :e2e:installDist`, then this file with
# `e2e/build/install/shoppers` as the context; `scripts/shoppers-check.sh` does both.

ARG TEMURIN=25.0.4_7

FROM eclipse-temurin:${TEMURIN}-jre
# Links the published package to the repository, whose visibility it then takes (B-27).
LABEL org.opencontainers.image.source="https://github.com/youndie/haul"
# Owned by the user it runs as: a distribution copied from a checkout with private file modes (a synced
# replica has them) is otherwise unreadable to uid 1000.
COPY --chown=1000:1000 . /opt/shoppers
USER 1000:1000
ENTRYPOINT ["/opt/shoppers/bin/shoppers"]
