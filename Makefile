default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -xtest

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

local-reset:
	cd flyway; make local-reset

do-reset:
	cd flyway; make do-reset

tests:
	./gradlew check

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

upgrade-wrapper:
	./gradlew wrapper --gradle-version=6.7 --distribution-type=bin