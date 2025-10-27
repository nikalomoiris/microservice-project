# PR checklist — Order status workflow

Use this checklist when opening the PR that implements order status lifecycle and event-driven reservation.

- [ ] Add migration for `orders.status` and `order_line_items.product_id` (if DB is managed externally).
- [ ] Unit tests added for listener and retry logic (yes/no)
- [ ] Integration tests (or manual smoke test steps) documented
- [ ] Consumers updated and deployed first (inventory-service) or made backward-compatible
- [ ] Message flow documented (`docs/message-flow.md`) — include sample JSON
- [ ] Observability: logs include `orderNumber`/`correlationId` in key places
- [ ] Idempotency: listeners validate current status before applying transitions
- [ ] Optimistic locking or other concurrency guards in place
- [ ] Rollout plan documented (order of deployment, rollback steps)
- [ ] Migration tested on a dev/staging database

Deployment order (recommended):
1. Deploy inventory-service that can accept the new `order.created` shape.
2. Deploy order-service producer (publishes enriched `OrderPlacedEvent`).
3. Monitor logs/metrics for failures and any unprocessed events.

If you cannot deploy consumers first, add backward-compatibility support in consumers to accept both old and new event shapes.
