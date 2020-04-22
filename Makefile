default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -xtest

build: compile

uberjar:
	./gradlew uberjar

uber: uberjar
	java -jar build/libs/server.jar

heroku:
	git push heroku master

tests:
	./gradlew check

versioncheck:
	./gradlew dependencyUpdates