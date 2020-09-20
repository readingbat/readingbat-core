
local-reset: local-clean local-migrate

local-clean:
	flyway -url=jdbc:postgresql://localhost:5432/readingbat clean

local-migrate:
	flyway -url=jdbc:postgresql://localhost:5432/readingbat migrate


cloud-reset: cloud-clean cloud-migrate

cloud-clean:
	flyway -url=jdbc:postgresql://159.203.227.25:5432/readingbat clean

cloud-migrate:
	flyway -url=jdbc:postgresql://159.203.227.25:5432/readingbat migrate


