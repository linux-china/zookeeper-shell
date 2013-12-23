ZooKeeper Shell
===============================
New ZooKeeper shell just like to bash to operate with ZooKeeper.


### Features

* current path & last previous path
* ls: list directory and files
* cd: change current path. support cd -
* cat: display content
* mkdir create directory
* create: touch demo.txt
* create or update: echo "" > demo.txt
* file:
* ZooKeeper commands: stat

### How to debug app

    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/zookeeper-shell-1.0.0-SNAPSHOT.jar
