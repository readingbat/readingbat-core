default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew --offline build -xtest

scan:
	./gradlew build --scan -xtest

build: compile

uberjar:
	./gradlew uberjar

uber: uberjar
	java -jar build/libs/server.jar

cc:
	./gradlew build --continuous -x test

run:
	./gradlew run

tests:
	./gradlew check

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies