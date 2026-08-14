-- V20: Adds sections_config column to support fully customizable resume
-- sections (add / remove / rename / reorder / show-hide / duplicate).
-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- sections_config is a JSON array, ordered by the user's chosen display
-- order, describing EVERY section in the resume (both standard sections
-- like Experience/Education/Skills and custom user-defined sections).
-- Each entry looks like:
--   {
--     "id": "experience",            -- stable id; for standard sections this
--                                     -- matches the underlying data key
--                                     -- (experience/education/skills/...).
--                                     -- For custom sections this is a
--                                     -- generated id (e.g. "custom-x7f3a1").
--     "type": "standard" | "custom",
--     "key": "experience",           -- only for type=standard: the resume
--                                     -- field this section reads from.
--     "label": "Work Experience",    -- user-editable display label
--                                     -- (renaming a standard section keeps
--                                     -- "key" the same but changes "label").
--     "visible": true,               -- show/hide toggle
--     "order": 3                     -- 0-based display order
--   }
--
-- Custom sections (type=custom) store their actual content inside the
-- existing custom_sections column, keyed by the same "id" used here.
-- sections_config is purely the ordering/visibility/label index;
-- custom_sections holds the content.
--
-- NULL is allowed: when absent, the application falls back to a built-in
-- default ordering so existing resumes created before this migration
-- continue to render unchanged.
--
-- Uses JSONB (not JSON) to match the type used by every other JSON column
-- in this schema (personal_info, experience, education, skills, etc. in
-- V1__initial_schema.sql) — JSONB also supports indexing/containment
-- queries that plain JSON does not.
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS sections_config JSONB;

-- PostgreSQL has no inline COMMENT clause on ALTER COLUMN/ADD COLUMN;
-- column comments are set with a separate COMMENT ON COLUMN statement.
COMMENT ON COLUMN resumes.sections_config IS
    'Ordered list of section descriptors controlling section order, visibility, and labels for both standard and custom sections. NULL = use default ordering.';
