ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS student_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_app_user_student_id'
          AND conrelid = 'app_user'::regclass
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT uq_app_user_student_id UNIQUE (student_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_app_user_student'
          AND conrelid = 'app_user'::regclass
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT fk_app_user_student
                FOREIGN KEY (student_id) REFERENCES student (id);
    END IF;
END
$$;
