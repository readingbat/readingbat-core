# Deployment Configurations

## Common

```
AGENT_ENABLED=true
GITHUB_OAUTH=
SENDGRID_API_KEY=
XFORWARDED_ENABLED=true
FILTER_LOG=true
PAPERTRAIL_PORT=
IPGEOLOCATION_KEY=
```

## localhost


## Digital Ocean

```
REDIS_URL=rediss://default:xxx@readingbat-redis-do-user-329986-0.a.db.ondigitalocean.com:port

POSTGRES_URL=jdbc:pgsql://readingbat-postgres.ondigitalocean.com:port/readingbat?ssl.mode=Require
POSTGRES_USERNAME=readingbat
POSTGRES_PASSWORD=
```

## Google Cloud Run

```
REDIS_URL=redis://ip_address:6379

DBMS_DRIVER_CLASSNAME=org.postgresql.Driver
POSTGRES_URL=jdbc:postgresql:///readingbat
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=
CLOUD_SQL_CONNECTION_NAME=readingbat-1:us-west1:readingbat-postgres
```