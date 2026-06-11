# Payment Execution

BIAN Service Domain microservice — **Phase 2b-c DEEP build** (graduated; see `.bian-graduated`). The execution half of the **payments flagship**.

| | |
|---|---|
| **Business Area / Domain** | Operations and Execution / Payments |
| **Pattern / Control Record** | Process / Payment Transaction Procedure |
| **K8s Namespace** | `bian-operations` |

## The debit-credit saga

```
RECEIVED ─debit ok─▶ DEBITED ─credit ok─▶ COMPLETED
   │                    └─credit fails─▶ COMPENSATING
   │                          ├─repay ok──▶ FAILED_COMPENSATED  (money back with debtor)
   │                          └─repay fails▶ FAILED_SUSPENSE    (funds in flight — OPS!)
   └─debit fails─▶ FAILED_DEBIT  (nothing moved)
```

- **Idempotent on `orderRef`** — re-submitting an executed order returns the existing execution; money never moves twice (also a DB unique index once hydrated).
- **`FAILED_SUSPENSE` is deliberately loud** — debit succeeded, credit *and* compensation failed. Emits `payment.suspense`, never auto-retried, indexed as the ops queue.
- Account legs run through the `AccountsClient` port. Phase 2b-c binds a **failure-injectable simulator** so every saga path is exercisable: account refs containing `FAIL-DEBIT`, `FAIL-CREDIT`, or `FAIL-COMPENSATE` drive each branch. The HTTP adapter against the real account SDs replaces it without touching the saga.

## API (contracts owned by this repo: [`api/openapi.yaml`](api/openapi.yaml), [`api/events.yaml`](api/events.yaml))

```bash
mvn spring-boot:run
CR=/v1/payment-transaction-procedure
# happy path
curl -s -X POST localhost:8080$CR/initiate -H 'content-type: application/json' \
  -d '{"orderRef":"PO-1","debtorAccountRef":"CA-D","creditorAccountRef":"CA-C","amountMinor":50000}'
# exercise compensation
curl -s -X POST localhost:8080$CR/initiate -H 'content-type: application/json' \
  -d '{"orderRef":"PO-2","debtorAccountRef":"CA-D","creditorAccountRef":"CA-FAIL-CREDIT-1","amountMinor":50000}'
```

## Persistence & tests

In-memory port/adapter. Postgres staged in [`db/schema.sql`](db/schema.sql) — gated. `mvn verify` proves all four saga outcomes and the idempotency guarantee.
