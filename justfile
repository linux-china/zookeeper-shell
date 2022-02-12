#!/usr/bin/env just --justfile

# build project
build:
  mvn -DskipTests package

# zookeeper shell
shell: build
  java -jar target/zookeeper-shell-1.0.0-SNAPSHOT.jar