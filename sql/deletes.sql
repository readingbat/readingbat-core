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
select 'server_requests', count(*)
from server_requests
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
where EXTRACT(YEAR FROM created) < 2026;

delete
from user_challenge_info
where EXTRACT(YEAR FROM created) < 2026;

delete
from geo_info
where EXTRACT(YEAR FROM created) < 2026;

