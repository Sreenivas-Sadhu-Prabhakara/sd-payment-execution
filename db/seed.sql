-- Sample data for local exploration after hydration. Idempotent.
INSERT INTO payment_execution (execution_id, order_ref, debtor_account_ref, creditor_account_ref,
                               amount_minor, currency, status, failure_reason, received_at, finished_at)
VALUES
    ('PE-SEED-0001', 'PO-SEED-0001', 'CA-SEED-0001', 'SA-SEED-0001', 250000, 'INR',
        'COMPLETED', NULL, now() - interval '1 day', now() - interval '1 day'),
    ('PE-SEED-0002', 'PO-SEED-0002', 'CA-SEED-0002', 'CA-SEED-0001', 100000, 'INR',
        'FAILED_COMPENSATED', 'CREDIT_FAILED:creditor account closed', now() - interval '2 hours', now() - interval '2 hours')
ON CONFLICT (execution_id) DO NOTHING;
