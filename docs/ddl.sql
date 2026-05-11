create table sessions (
    id bigint not null auto_increment,
    public_id char(36) not null,
    status varchar(30) not null,
    next_sequence bigint not null default 1,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    ended_at datetime(6) null,
    primary key (id),
    unique key uk_sessions_public_id (public_id),
    key idx_sessions_status_created_at (status, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table session_participants (
    id bigint not null auto_increment,
    session_id bigint not null,
    user_id varchar(100) not null,
    display_name varchar(100) not null,
    state varchar(30) not null,
    joined_at datetime(6) not null,
    left_at datetime(6) null,
    last_seen_at datetime(6) null,
    primary key (id),
    unique key uk_participants_session_user (session_id, user_id),
    key idx_participants_user_state (user_id, state),
    constraint fk_participants_session
        foreign key (session_id) references sessions (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table session_events (
    id bigint not null auto_increment,
    session_id bigint not null,
    server_sequence bigint not null,
    type varchar(40) not null,
    sender_id varchar(100) not null,
    client_event_id varchar(100) not null,
    client_sent_at datetime(6) null,
    server_received_at datetime(6) not null,
    payload_json json not null,
    primary key (id),
    unique key uk_events_session_sequence (session_id, server_sequence),
    unique key uk_events_idempotency (session_id, sender_id, client_event_id),
    key idx_events_session_received_sequence (session_id, server_received_at, server_sequence),
    key idx_events_sender_received (sender_id, server_received_at),
    constraint fk_events_session
        foreign key (session_id) references sessions (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table session_snapshots (
    id bigint not null auto_increment,
    session_id bigint not null,
    server_sequence bigint not null,
    snapshot_at datetime(6) not null,
    state_json json not null,
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_snapshots_session_sequence (session_id, server_sequence),
    key idx_snapshots_session_at (session_id, snapshot_at),
    constraint fk_snapshots_session
        foreign key (session_id) references sessions (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
