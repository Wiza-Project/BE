SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';

SELECT COUNT(*) FROM ncs_standard;
SELECT COUNT(*) FROM common_code;

SELECT * FROM ncs_standard;
SELECT * FROM common_code;
