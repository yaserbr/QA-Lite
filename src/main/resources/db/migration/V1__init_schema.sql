create table users (
    user_id bigserial primary key,
    username varchar(100) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_users_username unique (username),
    constraint ck_users_role check (role in ('ADMIN', 'QA_USER'))
);

create table environments (
    env_id bigserial primary key,
    name varchar(100) not null,
    description varchar(500),
    db_type varchar(30) not null,
    jdbc_url varchar(1000) not null,
    db_username varchar(200) not null,
    db_password_enc text not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_environments_name unique (name)
);

create table sql_definitions (
    sql_id bigserial primary key,
    sql_name varchar(200) not null,
    sql_description varchar(1000),
    sql_text text not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_sql_definitions_name unique (sql_name)
);

create table user_allowed_sql (
    user_id bigint not null references users(user_id) on delete cascade,
    sql_id bigint not null references sql_definitions(sql_id) on delete cascade,
    created_at timestamp not null default current_timestamp,
    primary key (user_id, sql_id)
);

create table user_allowed_env (
    user_id bigint not null references users(user_id) on delete cascade,
    env_id bigint not null references environments(env_id) on delete cascade,
    created_at timestamp not null default current_timestamp,
    primary key (user_id, env_id)
);

create table execution_history (
    execution_id bigserial primary key,
    user_id bigint not null references users(user_id),
    env_id bigint not null references environments(env_id),
    sql_id bigint not null references sql_definitions(sql_id),
    started_at timestamp not null default current_timestamp,
    status varchar(30) not null,
    records_returned integer,
    rows_affected integer,
    error_message varchar(2000),
    client_ip varchar(100),
    constraint ck_execution_history_status check (status in ('SUCCESS', 'FAILED'))
);

create index idx_user_allowed_sql_user on user_allowed_sql(user_id);
create index idx_user_allowed_sql_sql on user_allowed_sql(sql_id);
create index idx_user_allowed_env_user on user_allowed_env(user_id);
create index idx_user_allowed_env_env on user_allowed_env(env_id);
create index idx_execution_history_user on execution_history(user_id);
create index idx_execution_history_env on execution_history(env_id);
create index idx_execution_history_sql on execution_history(sql_id);
create index idx_execution_history_started on execution_history(started_at);
