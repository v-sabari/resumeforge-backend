UPDATE resumes SET template = 'modern' WHERE template = 'professional';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE  table_name      = 'resumes'
          AND  constraint_name = 'chk_resumes_template'
          AND  constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE resumes DROP CONSTRAINT chk_resumes_template;
    END IF;
END $$;

ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN ('modern','classic','minimal','executive','fresher','creative'));