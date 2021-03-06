# Notes

* [ktor Examples](https://github.com/ktorio/ktor-samples)
* [Scripting Examples](https://github.com/Kotlin/kotlin-script-examples)
* [Like/Dislike gifs](http://pngimg.com/imgs/symbols/like/)

## Running from *readingbat-core*
Setup:
* Main class: TestMain
* VM Options: -Dlogback.configurationFile=testresources/logback-test.xml
* Program Arguments: -config=readingbat-core/src/main/resources/application-dev.conf
* Environment Variables: IPGEOLOCATION_KEY=**value**;GITHUB_OAUTH=**Value**

## Heroku
* Switch shell to Java8 to get jvisualvm to work on an OSX client
* Create connection with: `heroku java:visualvm --app readingbat`
* Connect to shell with: `heroku ps:exec --app readingbat`
* hprof files are put in */tmp*
* Copy hprof file with: `heroku ps:copy /tmp/filename --app readingbat`
* Open in jvisualvm with file: File->Load... 
* https://devcenter.heroku.com/articles/exec#using-java-debugging-tools

## Prometheus
* Edit /etc/prometheus/prometheus.yml
* Resart with: `systemctl restart prometheus`
* Reset with: sudo systemctl reload prometheus.service
* Digital Ocean notes: https://marketplace.digitalocean.com/apps/prometheus?ipAddress=167.172.200.129#getting-started
* Prometheus admin: http://metrics.readingbat.com:9090/graph


## Grafana
* JVM dashboard is here: https://grafana.com/grafana/dashboards/8563
* World map: https://grafana.com/grafana/plugins/grafana-worldmap-panel
* Config file is: `/etc/grafana/grafana.ini`
* Log file is: `/var/log/grafana/grafana.log`

## PrismJs
* Look in .js files to find URLs to load css and js files

## JMX Exporter
* https://github.com/prometheus/jmx_exporter

## Setting up builds on Linux
* https://sdkman.io/install
* apt-get install unzip zip make
* curl -s "https://get.sdkman.io" | bash
* sdk install java 
* sdk install kotlin
* sdk install gradle
* docker login

## Postgres

* [Hikari](https://github.com/brettwooldridge/HikariCP)
* [Postgres on Docker](https://hackernoon.com/dont-install-postgres-docker-pull-postgres-bee20e200198)
* Running postgres on docker:

```shell
  mkdir -p $HOME/docker/volumes/postgres
  docker run --rm --name pg-docker -e POSTGRES_PASSWORD=docker -d -p 5432:5432 -v $HOME/docker/volumes/postgres:/var/lib/postgresql/data  postgres
```

* Running Postgres locally

```shell
  psql -h localhost -U postgres -d postgres #  use password: docker
  psql "dbname=readingbat host=localhost port=5432 user=postgres password=docker"
```

## Exposed

* [Exposed](https://github.com/JetBrains/Exposed)
* [Notes on Postgres/Exposed](https://www.thebookofjoel.com/kotlin-ktor-exposed-postgres)
* [Upsert 1](https://github.com/JetBrains/Exposed/issues/167)
* [Upsert 2](https://medium.com/@OhadShai/first-steps-with-kotlin-exposed-cb361a9bf5ac)

## Google Cloud Postgres

* https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory
* ./bin/cloud_sql_proxy -instances=${ID}:${region}:readingbat-postgres=tcp:5432

## Cypress.io

* tab plugin: https://github.com/Bkucera/cypress-plugin-tab
* Start test server with: `./testdata.sh`
* Clear data with: `make dbreset`
* To start locally from repo root: `~/node_modules/.bin/cypress open`
* To run: `~/node_modules/.bin/cypress run --record --key 5ee5de19-1e84-4807-a199-5c70fda2fe5d`
* https://levelup.gitconnected.com/what-ive-learnt-using-cypress-io-for-the-past-three-weeks-c1597999cd2f