# Notes

* [ktor Examples](https://github.com/ktorio/ktor-samples)
* [Like/Dislike gifs](http://pngimg.com/imgs/symbols/like/)

## Running from *readingbat-core*
Setup:
* VM Options: -Dlogback.configurationFile=src/test/resources/logback-test.xml
* Program Arguments: -config=src/main/resources/application-dev.conf
* Environment Variables: SENDGRID_API_KEY=**value**

## Heroku Notes
* Switch shell to Java8 to get jvisualvm to work on an OSX client
* Create connection with: `heroku java:visualvm --app readingbat`
* Connect to shell with: `heroku ps:exec --app readingbat`
* hprof files are put in */tmp*
* Copy hprof file with: `heroku ps:copy /tmp/filename --app readingbat`
* Open in jvisualvm with file: File->Load... 
* https://devcenter.heroku.com/articles/exec#using-java-debugging-tools

## Prometheus Notes
* Edit /etc/prometheus/prometheus.yml
* Reset with: sudo systemctl reload prometheus.service
* Digital Ocean notes: https://marketplace.digitalocean.com/apps/prometheus?ipAddress=167.172.200.129#getting-started
* Prometheus admin: http://metrics.readingbat.com:9090/graph


## Grafana Notes
* JVM dashboard is here: https://grafana.com/grafana/dashboards/8563

## PrismJs Notes
* Look in .js files to find URLs to load css and js files
