select 'browser_sessions' as tablename, count(*) as count
from browser_sessions
union
select 'classes', count(*)
from classes
union
select 'enrollees', count(*)
from enrollees
union
select 'geo_info', count(*)
from geo_info
union
select 'password_resets', count(*)
from password_resets
union
select 'server_requests', count(*)
from server_requests
union
select 'session_answer_history', count(*)
from session_answer_history
union
select 'session_challenge_info', count(*)
from session_challenge_info
union
select 'user_answer_history', count(*)
from user_answer_history
union
select 'user_challenge_info', count(*)
from user_challenge_info
union
select 'user_sessions', count(*)
from user_sessions
union
select 'users', count(*)
from users;


delete
from server_requests;

delete
from browser_sessions;

delete
from user_answer_history
where EXTRACT(YEAR FROM created) != 2025;

delete
from user_challenge_info
where EXTRACT(YEAR FROM created) != 2025;

delete
from geo_info
where EXTRACT(YEAR FROM created) != 2025;


select count(*)
from session_answer_history
where EXTRACT(YEAR FROM created) = 2025;


select count(*)
from session_challenge_info
where EXTRACT(YEAR FROM created) = 2025;



select count(*)
from user_answer_history
where EXTRACT(YEAR FROM created) != 2025;

select EXTRACT(MONTH FROM created)
from user_answer_history
SELECT EXTRACT(MONTH FROM created) AS month,
       COUNT(*)                    AS employee_count
FROM employees
GROUP BY EXTRACT(MONTH FROM date_of_birth)

