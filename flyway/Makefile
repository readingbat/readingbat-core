
local-reset: local-clean local-migrate

local-clean:
	flyway -locations="filesystem:../src/main/resources/sql" -user=postgres -password=docker -url=jdbc:postgresql://localhost:5432/readingbat clean

local-migrate:
	flyway -locations="filesystem:../src/main/resources/sql" -user=postgres -password=docker -url=jdbc:postgresql://localhost:5432/readingbat migrate


do-reset: do-clean do-migrate

do-clean:
	flyway -locations="filesystem:../src/main/resources/sql" -user=readingbat -password=zzz -url=jdbc:postgresql://readingbat-postgres-do-user-329986-0.b.db.ondigitalocean.com:25060/readingbat clean

do-migrate:
	flyway -locations="filesystem:../src/main/resources/sql" -user=readingbat -password=zzz -url=jdbc:postgresql://readingbat-postgres-do-user-329986-0.b.db.ondigitalocean.com:25060/readingbat migrate


