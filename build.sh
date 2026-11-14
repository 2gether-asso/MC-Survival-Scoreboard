#!/bin/sh
set -eu

# Builds the plugin jar in Docker (no local JDK needed): build/libs/MC-Survival-Scoreboard-<version>.jar
cd "$(dirname "$0")"
# (the Gradle cache lives in .gradle-home so it belongs to the current user)
mkdir -p .gradle-home
docker run --rm \
	-u "$(id -u):$(id -g)" \
	-e GRADLE_USER_HOME=/project/.gradle-home \
	-v "$PWD:/project" \
	-w /project \
	gradle:jdk25 gradle --no-daemon build
ls -1 build/libs/*.jar
