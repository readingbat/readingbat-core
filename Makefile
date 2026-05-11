.PHONY: default help stop tw-css tw-full-css clean clean-all build scan uberjar uber run tests remote-tests \
        coverage coverage-html coverage-xml coverage-log coverage-verify coverage-open coverage-packages coverage-clean \
        dbinfo dbclean dbmigrate dbvalidate lint detekt detekt-baseline depends versioncheck kdocs clean-docs \
        site publish-local publish-local-snapshot publish-snapshot \
        publish-maven-central upgrade-wrapper \
        _check-gpg-env _require-version _require-gradle-version

VERSION := $(shell grep -E '^version=' gradle.properties | cut -d= -f2)
GRADLE_VERSION := $(shell grep -E '^gradle[[:space:]]*=' gradle/libs.versions.toml | sed -E 's/.*"([^"]+)".*/\1/')

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys "$$GPG_SIGNING_KEY_ID")" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyId="$$GPG_SIGNING_KEY_ID" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)"

default: versioncheck

help: ## Show this help message
	@awk 'BEGIN { FS = ":.*## "; printf "Available targets:\n\n" } \
	/^[a-zA-Z_-]+:.*## / { printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

stop: ## Stop the Gradle daemon
	./gradlew --stop

tw-css: ## Build Tailwind CSS (incremental)
	./gradlew :readingbat-core:tailwindBuild

tw-full-css: ## Build Tailwind CSS (full)
	./gradlew :readingbat-core:tailwindBuildFull

clean: ## Clean build outputs
	./gradlew clean

clean-all: clean clean-docs ## Clean build outputs, .gradle caches, and docs
	rm -rf .gradle readingbat-core/.gradle readingbat-kotest/.gradle

build: tw-css ## Build the project (skips tests)
	./gradlew build -x test

scan: ## Build with a Gradle build scan (skips tests)
	./gradlew build --scan -x test

uberjar: ## Build the executable uberjar
	./gradlew uberjar

uber: uberjar ## Build and run the uberjar
	java -jar build/libs/server.jar

run: ## Run the application
	./gradlew run

tests: ## Run all tests
	./gradlew check

coverage: coverage-html coverage-xml ## Generate HTML and XML coverage reports

coverage-html: ## Generate HTML coverage report
	./gradlew koverHtmlReport

coverage-xml: ## Generate XML coverage report
	./gradlew koverXmlReport

coverage-log: ## Print coverage summary to log
	./gradlew koverLog

coverage-verify: ## Verify coverage threshold
	./gradlew koverVerify

coverage-open: coverage-html ## Open the HTML coverage report in a browser
	open build/reports/kover/html/index.html

coverage-packages: coverage-xml ## Print per-package coverage breakdown
	@python3 scripts/coverage_packages.py

coverage-clean: ## Remove coverage reports and test results
	./gradlew cleanTest
	rm -rf build/reports/kover build/kover

remote-tests: ## Run Playwright endpoint tests against readingbat.com
	TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "PlaywrightEndpointTest"

dbinfo: ## Show Flyway migration status
	./gradlew flywayInfo

dbclean: ## Drop all database objects via Flyway
	./gradlew flywayClean

dbmigrate: ## Run Flyway migrations
	./gradlew flywayMigrate

dbvalidate: ## Validate applied migrations against scripts
	./gradlew flywayValidate

lint: ## Run Kotlinter and detekt
	./gradlew lintKotlin detekt

detekt: ## Run detekt static analysis
	./gradlew detekt

detekt-baseline: ## Generate detekt baseline file
	./gradlew detektBaseline

depends: ## Show project dependency tree
	./gradlew dependencies

versioncheck: ## Report available dependency updates
	./gradlew dependencyUpdates --no-parallel

kdocs: ## Generate Dokka HTML documentation
	./gradlew dokkaGeneratePublicationHtml

clean-docs: ## Remove generated website artifacts
	rm -rf website/readingbat-core/site website/readingbat-core/.cache

site: clean-docs ## Serve the docs site locally with zensical
	(cd website/readingbat-core && uv run zensical serve)

publish-local: _require-version ## Publish artifacts to the local Maven repo
	./gradlew publishToMavenLocal

publish-local-snapshot: _require-version ## Publish a -SNAPSHOT to the local Maven repo
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

publish-snapshot: _require-version _check-gpg-env ## Publish a signed -SNAPSHOT to Maven Central
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: _require-version _check-gpg-env ## Publish and release a signed version to Maven Central
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper: _require-gradle-version ## Upgrade the Gradle wrapper to the catalog version
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin

_check-gpg-env:
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "ERROR: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "ERROR: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if ! security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w >/dev/null 2>&1; then \
		echo "ERROR: keychain entry 'gradle-signing-password' (account 'gpg-signing') not found" >&2; exit 1; \
	fi

_require-version:
	@[ -n "$(VERSION)" ] || { echo "ERROR: Could not determine project version from gradle.properties" >&2; exit 1; }

_require-gradle-version:
	@[ -n "$(GRADLE_VERSION)" ] || { echo "ERROR: Could not determine gradle version from gradle/libs.versions.toml" >&2; exit 1; }
