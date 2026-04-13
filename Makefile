VERSION := $(shell grep 'extra\["versionStr"\]' build.gradle.kts | grep -o '"[0-9][^"]*"' | tr -d '"')

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
	./gradlew check --rerun-tasks

remote-tests:
	TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "PlaywrightEndpointTest"

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
	./gradlew lintKotlinMain lintKotlinTest

test:
	~/node_modules/.bin/cypress open

depends:
	./gradlew dependencies

versioncheck:
	./gradlew dependencyUpdates

kdocs:
	./gradlew dokkaGeneratePublicationHtml

clean-docs:
	rm -rf website/readingbat-core/site
	rm -rf website/readingbat-core/.cache

site: clean-docs
	cd website/readingbat-core && uv run zensical serve

publish-local:
	./gradlew publishToMavenLocal

publish-local-snapshot:
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)

publish-snapshot:
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central:
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.4.1 --distribution-type=bin
