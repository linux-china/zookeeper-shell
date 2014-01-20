#!/bin/bash

app_dir=`dirname $0`
java -jar ${app_dir}/../zookeeper-shell-1.0.0-SNAPSHOT.jar $*
