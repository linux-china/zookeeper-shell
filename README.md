ZooKeeper Shell
===============================
New ZooKeeper shell just like to bash to operate with ZooKeeper.


### Features

* current path & last previous path
* ls: list directory and files
* cd: change current path. support cd -
* cat: display content
* create: touch demo.txt
* create or update: echo "" > demo.txt
* stat: stat file
* ZooKeeper commands: stat
* Completion for directory and file

### How to debug app

    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/zookeeper-shell-1.0.0-SNAPSHOT.jar
                            
### References

* Spring Shell Reference Documentation: https://docs.spring.io/spring-shell/docs/2.1.0-M2/site/reference/htmlsingle/ 