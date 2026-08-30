insert into users (username, password_hash, role)
select 'admin', '$2a$10$q.9rdducGdo/2aDKnglx2OHHQ7MHHBA3ezN28N7ppzPczri2Qxfea', 'ADMIN'
from dual
where not exists (
    select 1 from users where username = 'admin'
);

insert into users (username, password_hash, role)
select 'qa_user', '$2b$10$uAvn25cbAKzrs66W7iApwuwmlJ7WP.WWycX6pdllK6ZGprfH0lJja', 'QA_USER'
from dual
where not exists (
    select 1 from users where username = 'qa_user'
);

insert into environments (name, description, db_type, jdbc_url, db_username, db_password_enc)
select
    'SIT',
    'System integration testing environment',
    'POSTGRESQL',
    'jdbc:postgresql://sit.example.local:5432/appdb',
    'qa_readonly',
    'encrypted-placeholder'
from dual
where not exists (
    select 1 from environments where name = 'SIT'
);

insert into environments (name, description, db_type, jdbc_url, db_username, db_password_enc)
select
    'UAT',
    'User acceptance testing environment',
    'POSTGRESQL',
    'jdbc:postgresql://uat.example.local:5432/appdb',
    'qa_readonly',
    'encrypted-placeholder'
from dual
where not exists (
    select 1 from environments where name = 'UAT'
);

insert into sql_definitions (sql_name, sql_description, sql_text)
select
    'Get Sample Customers',
    'Returns a small sample customer list',
    'select 101 as customer_id, ''Ahmed'' as customer_name union all select 102, ''Sara'''
from dual
where not exists (
    select 1 from sql_definitions where sql_name = 'Get Sample Customers'
);

insert into sql_definitions (sql_name, sql_description, sql_text)
select
    'Count Sample Orders',
    'Returns a sample order count',
    'select 42 as order_count'
from dual
where not exists (
    select 1 from sql_definitions where sql_name = 'Count Sample Orders'
);

insert into user_allowed_env (user_id, env_id)
select u.user_id, e.env_id
from users u
cross join environments e
where u.username = 'qa_user'
  and e.name in ('SIT', 'UAT')
  and not exists (
      select 1
      from user_allowed_env existing_access
      where existing_access.user_id = u.user_id
        and existing_access.env_id = e.env_id
  );

insert into user_allowed_sql (user_id, sql_id)
select u.user_id, s.sql_id
from users u
cross join sql_definitions s
where u.username = 'qa_user'
  and s.sql_name in ('Get Sample Customers', 'Count Sample Orders')
  and not exists (
      select 1
      from user_allowed_sql existing_access
      where existing_access.user_id = u.user_id
        and existing_access.sql_id = s.sql_id
  );

insert into execution_history (
    user_id,
    env_id,
    sql_id,
    status,
    records_returned,
    rows_affected,
    error_message,
    client_ip
)
select
    u.user_id,
    e.env_id,
    s.sql_id,
    'SUCCESS',
    2,
    null,
    null,
    '127.0.0.1'
from users u
join environments e on e.name = 'SIT'
join sql_definitions s on s.sql_name = 'Get Sample Customers'
where u.username = 'qa_user'
  and not exists (
      select 1
      from execution_history history
      where history.user_id = u.user_id
        and history.env_id = e.env_id
        and history.sql_id = s.sql_id
        and history.client_ip = '127.0.0.1'
  );
