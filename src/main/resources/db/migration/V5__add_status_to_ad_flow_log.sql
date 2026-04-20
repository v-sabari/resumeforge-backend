ALTER TABLE ad_flow_log
ADD COLUMN IF NOT EXISTS status VARCHAR(50);

UPDATE ad_flow_log
SET status = 'UNKNOWN'
WHERE status IS NULL;