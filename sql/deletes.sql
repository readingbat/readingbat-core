SELECT 'browser_sessions' AS tablename, count(*) AS count
FROM browser_sessions
UNION
SELECT 'classes', count(*)
FROM classes
UNION
SELECT 'enrollees', count(*)
FROM enrollees
UNION
SELECT 'geo_info', count(*)
FROM geo_info
UNION
SELECT 'server_requests', count(*)
FROM server_requests
UNION
SELECT 'user_answer_history', count(*)
FROM user_answer_history
UNION
SELECT 'user_challenge_info', count(*)
FROM user_challenge_info
UNION
SELECT 'user_sessions', count(*)
FROM user_sessions
UNION
SELECT 'users', count(*)
FROM users;


DELETE
FROM server_requests;

DELETE
FROM browser_sessions;

DELETE
FROM user_answer_history
WHERE EXTRACT(YEAR FROM created) < 2026;

DELETE
FROM user_challenge_info
WHERE EXTRACT(YEAR FROM created) < 2026;

DELETE
FROM geo_info
WHERE EXTRACT(YEAR FROM created) < 2026;

