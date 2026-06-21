CREATE TABLE idempotency_keys (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(255)  NOT NULL UNIQUE,
    request_hash    VARCHAR(64)   NOT NULL,
    response_body   TEXT          NOT NULL,
    response_status INT           NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);