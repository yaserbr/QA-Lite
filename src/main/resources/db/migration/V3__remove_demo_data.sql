-- Removes the demo data seeded by V2__seed_sample_data.sql (SIT/UAT sample environments,
-- the qa_user sample account, and the two demo SQL commands) so a real deployment doesn't
-- launch with placeholder configuration. The admin account from V2 is left untouched.
-- Deletion order respects the foreign keys in V1__init_schema.sql (execution_history
-- references environments/sql_definitions without ON DELETE CASCADE).

delete from execution_history
where env_id in (select env_id from environments where name in ('SIT', 'UAT'))
   or sql_id in (select sql_id from sql_definitions where sql_name in ('Get Sample Customers', 'Count Sample Orders'));

delete from user_allowed_env
where env_id in (select env_id from environments where name in ('SIT', 'UAT'));

delete from user_allowed_sql
where sql_id in (select sql_id from sql_definitions where sql_name in ('Get Sample Customers', 'Count Sample Orders'));

delete from environments where name in ('SIT', 'UAT');

delete from sql_definitions where sql_name in ('Get Sample Customers', 'Count Sample Orders');

delete from execution_history
where user_id in (select user_id from users where username = 'qa_user');

delete from user_allowed_env
where user_id in (select user_id from users where username = 'qa_user');

delete from user_allowed_sql
where user_id in (select user_id from users where username = 'qa_user');

delete from users where username = 'qa_user';
