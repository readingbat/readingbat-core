VERSION := $(shell grep 'extra\["versionStr"\]' build.gradle.kts | grep -o '"[0-9][^"]*"' | tr -d '"')
JITPACK_BUILD_LOG := https://jitpack.io/com/github/readingbat/readingbat-core/$(VERSION)/build.log
JITPACK_BUILD_API := https://jitpack.io/api/builds/com.github.readingbat/readingbat-core/$(VERSION)

default: versioncheck

stop:
	./gradlew --stop

clean:
	./gradlew clean

build: clean tw-css
	./gradlew build -xtest

tw-css:
	./gradlew :readingbat-core:tailwindBuild

tw-full-css:
	./gradlew :readingbat-core:tailwindBuildFull

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

trigger-jitpack:
	until curl -s "$(JITPACK_BUILD_LOG)" | grep -qv "not found"; do \
		echo "Waiting for JitPack..."; \
		sleep 10; \
	done
	echo "JitPack build complete for version ${VERSION}"

view-jitpack:
	curl -s "$(JITPACK_BUILD_LOG)"
	curl -s "$(JITPACK_BUILD_API)" | jq

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.4.0 --distribution-type=bin
