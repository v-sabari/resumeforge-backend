UPDATE ad_flow_log
SET status = 'UNKNOWN'
WHERE status IS NULL;