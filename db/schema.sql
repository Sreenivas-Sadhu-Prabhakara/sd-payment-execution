-- Payment Execution service domain — Postgres schema.
-- READY TO HYDRATE, NOT YET WIRED (see platform-infra/postgres/hydrate.sh).

CREATE TABLE IF NOT EXISTS payment_execution (
    execution_id        VARCHAR(40)  PRIMARY KEY,
    order_ref           VARCHAR(40),
    debtor_account_ref  VARCHAR(60)  NOT NULL,
    creditor_account_ref VARCHAR(60) NOT NULL,
    amount_minor        BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(20)  NOT NULL
        CHECK (status IN ('RECEIVED','DEBITED','COMPLETED','COMPENSATING',
                          'FAILED_DEBIT','FAILED_COMPENSATED','FAILED_SUSPENSE')),
    failure_reason      VARCHAR(280),
    received_at         TIMESTAMPTZ  NOT NULL,
    finished_at         TIMESTAMPTZ
);

-- the idempotency guarantee: one execution per payment order
CREATE UNIQUE INDEX IF NOT EXISTS uq_pe_order_ref
    ON payment_execution (order_ref) WHERE order_ref IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pe_status ON payment_execution (status);
-- FAILED_SUSPENSE is the ops queue — keep it instantly findable
CREATE INDEX IF NOT EXISTS idx_pe_suspense
    ON payment_execution (finished_at) WHERE status = 'FAILED_SUSPENSE';
