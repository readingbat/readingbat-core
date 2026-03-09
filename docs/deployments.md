# Deployment Configurations

## Common

```
AGENT_ENABLED=true
GITHUB_OAUTH=
XFORWARDED_ENABLED=true
FILTER_LOG=true
PAPERTRAIL_PORT=
IPGEOLOCATION_KEY=
```

## localhost
```
DBMS_URL=jdbc:pgsql://host.docker.internal:5432/readingbat
DBMS_USERNAME=postgres
DBMS_PASSWORD=docker
```

## Digital Ocean

```
DBMS_URL=jdbc:pgsql://readingbat-postgres.ondigitalocean.com:port/readingbat?ssl.mode=Require
DBMS_USERNAME=readingbat
DBMS_PASSWORD=
```

## Google Cloud Run

```
DBMS_DRIVER_CLASSNAME=org.postgresql.Driver
DBMS_URL=jdbc:postgresql:///readingbat
DBMS_USERNAME=postgres
DBMS_PASSWORD=
CLOUD_SQL_CONNECTION_NAME=readingbat-1:us-west1:readingbat-postgres
```
