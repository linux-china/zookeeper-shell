#!/bin/bash

app_dir=`dirname $0`
java -jar ${app_dir}/../target/zookeeper-shell-1.0.0-SNAPSHOT.jar $*
