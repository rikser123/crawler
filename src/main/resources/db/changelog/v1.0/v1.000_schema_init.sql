CREATE TABLE request (
     id UUID PRIMARY KEY,
     user_id UUID NOT NULL,
     query_text VARCHAR(400) NOT NULL,
     family_mode VARCHAR(50),
     groups_on_page VARCHAR(20),
     status VARCHAR(40),
     updated TIMESTAMP WITH TIME ZONE,
     created TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE request_result (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    saved_copy_url TEXT,
    url TEXT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    mod_time TIMESTAMP WITH TIME ZONE,
    size BIGINT,
    charset VARCHAR(50),
    mime_type VARCHAR(100),
    passages TEXT,
    request_id UUID,
    created TIMESTAMP WITH TIME ZONE NOT NULL,
    updated TIMESTAMP WITH TIME ZONE
);

CREATE TABLE kafka_request_message (
    id UUID PRIMARY KEY,
    dto JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created TIMESTAMP WITH TIME ZONE NOT NULL,
    updated TIMESTAMP WITH TIME ZONE
);

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE request_per_day_limit (
   id UUID PRIMARY KEY,
   user_id UUID NOT NULL,
   start_time TIMESTAMP WITH TIME ZONE NOT NULL,
   end_time TIMESTAMP WITH TIME ZONE NOT NULL,
   request_count INTEGER NOT NULL,
   request_limit INTEGER NOT NULL,
   created TIMESTAMP WITH TIME ZONE NOT NULL,
   updated TIMESTAMP WITH TIME ZONE
);

