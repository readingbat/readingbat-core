.PHONY: default help stop tw-css tw-full-css clean clean-all build scan uberjar uber run tests remote-tests \
        coverage coverage-html coverage-xml coverage-log coverage-verify coverage-open coverage-packages coverage-clean \
        dbinfo dbclean dbmigrate dbreset dbvalidate lint detekt detekt-baseline depends versioncheck kdocs clean-docs \
        site publish-local publish-local-snapshot check-gpg-env publish-snapshot \
        publish-maven-central upgrade-wrapper

VERSION := $(shell grep -E '^version=' gradle.properties | cut -d= -f2)

ifeq ($(strip $(VERSION)),)
$(error Could not determine project version from gradle.properties)
endif

GRADLE_VERSION := $(shell grep -E '^gradle[[:space:]]*=' gradle/libs.versions.toml | sed -E 's/.*"([^"]+)".*/\1/')

ifeq ($(strip $(GRADLE_VERSION)),)
$(error Could not determine gradle version from gradle/libs.versions.toml)
endif

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
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
	./gradlew build -xtest

scan: ## Build with a Gradle build scan (skips tests)
	./gradlew build --scan -xtest

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
	@python3 -c "import xml.etree.ElementTree as ET; \
r = ET.parse('build/reports/kover/report.xml').getroot(); \
pkgs = []; \
[pkgs.append((p.get('name'), int(c.get('covered')), int(c.get('missed')))) \
 for p in r.findall('package') for c in p.findall('counter') if c.get('type') == 'INSTRUCTION']; \
pkgs.sort(key=lambda x: -x[2]); \
print(f\"{'package':<55} {'cov%':>6} {'covered':>9} {'missed':>9} {'total':>9}\"); \
[print(f'{n:<55} {(c/(c+m)*100 if c+m else 0):6.1f} {c:9d} {m:9d} {c+m:9d}') for n,c,m in pkgs]; \
tc=sum(p[1] for p in pkgs); tm=sum(p[2] for p in pkgs); \
print(f'\nOVERALL: {tc/(tc+tm)*100:.2f}% ({tc}/{tc+tm} instructions, {tm} missed)')"

coverage-clean: ## Remove coverage reports and test results
	./gradlew cleanAllTests
	rm -rf build/reports/kover build/kover

remote-tests: ## Run Playwright endpoint tests against readingbat.com
	TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "PlaywrightEndpointTest"

dbinfo: ## Show Flyway migration status
	./gradlew flywayInfo

dbclean: ## Drop all database objects via Flyway
	./gradlew flywayClean

dbmigrate: ## Run Flyway migrations
	./gradlew flywayMigrate

dbreset: dbclean dbmigrate ## Drop and re-apply all migrations

dbvalidate: ## Validate applied migrations against scripts
	./gradlew flywayValidate

lint: detekt ## Run Kotlinter and detekt
	./gradlew lintKotlinMain lintKotlinTest

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

publish-local: ## Publish artifacts to the local Maven repo
	./gradlew publishToMavenLocal

publish-local-snapshot: ## Publish a -SNAPSHOT to the local Maven repo
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

check-gpg-env: ## Verify GPG signing env and keychain entry are present
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "Error: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "Error: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if ! security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w >/dev/null 2>&1; then \
		echo "Error: keychain entry 'gradle-signing-password' (account 'gpg-signing') not found" >&2; exit 1; \
	fi

publish-snapshot: check-gpg-env ## Publish a signed -SNAPSHOT to Maven Central
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: check-gpg-env ## Publish and release a signed version to Maven Central
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper: ## Upgrade the Gradle wrapper to the catalog version
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin
