default: versioncheck

stop:
	./gradlew --stop

clean:
	./gradlew clean

build: clean
	./gradlew build -xtest

publish:
	./gradlew publishToMavenLocal

scan:
	./gradlew build --scan -xtest

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

dbinfo:
	./gradlew flywayInfo

dbclean:
	./gradlew flywayClean

dbmigrate:
	./gradlew flywayMigrate

dbreset: dbclean dbmigrate

dbvalidate:
	./gradlew flywayValidate

lint:
	./gradlew lintKotlinMain
	./gradlew lintKotlinTest

test:
	~/node_modules/.bin/cypress open

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.0.0 --distribution-type=bin
