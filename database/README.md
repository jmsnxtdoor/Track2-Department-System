# PostgreSQL setup
Create a database and dedicated local development user:
```sql
CREATE USER department WITH PASSWORD 'replace_with_a_strong_password';
CREATE DATABASE department_db OWNER department;
```
Spring Data JPA creates initial tables when the backend starts (`ddl-auto=update`). For production use, switch to schema migrations and `validate` instead. No financial seed data is inserted.

## Student account links
Before starting the backend with `ddl-auto=validate`, apply
[`migrations/20261029_add_student_account_link.sql`](./migrations/20261029_add_student_account_link.sql)
to add the nullable, unique link from a student account to a student record. The migration
does not update or create account or student records. Existing student accounts remain
unlinked until an administrator links them in User Accounts.

For example, from the project root, run `psql -h <host> -U <database-user> -d <database-name> -f database/migrations/20261029_add_student_account_link.sql`.
