# testrail-quack-migrate
1. mvn clean install
It will build a fat-jar in target directory
2. cd target
3. Run JAR
```java -jar testrail-quack-migration-1.10-SNAPSHOT-jar-with-dependencies.jar <API_ENDPOINT> <PROJECT_ID> <ABSOLUTE_PATH_TO_XML_FILE> <API_TOKEN>```
e.g.
```java -jar testrail-quack-migration-1.10-SNAPSHOT-jar-with-dependencies.jar http://quack.com/api/ test2 /tmp/test_testrail_functions.xml abc```
