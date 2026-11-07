# NORD-CODES

Задача покрыть тестами java-приложение

## Запуск
### 1. Убедиться, что Maven видит Java 17
mvn -version

### 2. Собрать и прогнать тесты
mvn clean test

### 3. Собрать jar
mvn clean package

### 4. Посмотреть Allure-отчёт (локальный сервер)
mvn allure:serve

### 5. Запустить jar — теперь без ошибки UnsupportedClassVersionError
java -jar -Dsecret=qazWSXedc -Dmock=http://localhost:8888/ target/internal-0.0.1-SNAPSHOT.jar
