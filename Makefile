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

dbinfo:
	./gradlew flywayInfo

dbclean:
	./gradlew flywayClean

dbmigrate:
	./gradlew flywayMigrate

dbvalidate:
	./gradlew flywayValidate

test:
	~/node_modules/.bin/cypress open

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

upgrade-wrapper:
	./gradlew wrapper --gradle-version=6.7.1 --distribution-type=bin