# Deployment Configurations

The server connects with the PostgreSQL JDBC driver (`org.postgresql.Driver`, the default for
`DBMS_DRIVER_CLASSNAME`). pgjdbc-ng's `jdbc:pgsql:` URLs and its `ssl.mode` parameter are rejected
at startup: use `jdbc:postgresql:` and `sslmode`.

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
DBMS_URL=jdbc:postgresql://host.docker.internal:5432/readingbat
DBMS_USERNAME=postgres
DBMS_PASSWORD=docker
```

## Digital Ocean

```
DBMS_URL=jdbc:postgresql://readingbat-postgres.ondigitalocean.com:port/readingbat?sslmode=require
DBMS_USERNAME=readingbat
DBMS_PASSWORD=
```

`sslmode=require` encrypts the connection without verifying the server's certificate, as pgjdbc-ng's
`ssl.mode=Require` did. To verify it, use `sslmode=verify-full&sslrootcert=<path>` with the
cluster's CA certificate from the Digital Ocean console.

## Google Cloud Run

```
DBMS_URL=jdbc:postgresql:///readingbat
DBMS_USERNAME=postgres
DBMS_PASSWORD=
CLOUD_SQL_CONNECTION_NAME=readingbat-1:us-west1:readingbat-postgres
```
