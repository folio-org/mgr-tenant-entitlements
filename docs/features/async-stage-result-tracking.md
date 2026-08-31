---
feature_id: async-stage-result-tracking
title: Async Stage Result Tracking
updated: 2026-08-26
---

# Async Stage Result Tracking

## What it does

Allows event-publishing stages (capabilities, system-user, scheduled-job) to remain `IN_PROGRESS` after sending a Kafka event, and advance to `FINISHED` or `FAILED` only when the downstream consumer reports the outcome back via the `mgr-tenant-entitlements.resource-result` Kafka topic. The service correlates inbound results to the originating stage using a UUID embedded as the `id` field of every outbound `ResourceEvent`. When the result arrives, the stage, the application flow, and the top-level flow are all advanced to their terminal statuses in a single transaction.

Flows that never receive a confirmation are failed by a periodic sweeper once a configurable timeout elapses.

## Why it exists

Previously, publishing a Kafka event ended the publishing stage immediately as `FINISHED` regardless of whether the downstream system (e.g. mod-roles-keycloak processing capabilities) had actually completed its work. This made it impossible to track async outcomes and caused flows to be reported as finished before their real work was done (MGRENTITLE-158).

## Entry point(s)

**Inbound — Kafka consumer**

| Listener ID | Topic pattern | Group ID |
|-------------|--------------|----------|
| `resource-result-event-listener` | `KAFKA_RESOURCE_RESULT_TOPIC_PATTERN` (default `${ENV}.mgr-tenant-entitlements.resource-result`) | `${ENV}-mgr-tenant-entitlements-resource-result-group` |

Processes `ResourceResultEvent` messages via `ResourceResultEventService`. The event's `id` field must be the UUID of an existing `flow_stage` row (`stage_id` column). Listener container factory: `resourceResultContainerFactory`.

**Outbound — stage UUID in published events**

Each capability, system-user, and scheduled-job Kafka event now carries the publishing stage's UUID as `ResourceEvent.id`. Downstream services use this value as the `id` field of the `ResourceResultEvent` they send back.

**REST — stage ID exposure**

The `id` field on `FlowStage` (returned by `GET /entitlement-flows/{flowId}?includeStages=true` and `GET /entitlement-flows/{flowId}/app-flows/{appFlowId}/stages`) exposes the same UUID, allowing callers to correlate stage records with async results.

**Scheduled — stale-flow sweeper**

`AsyncConfirmationSweeper.failStaleAsyncFlows()` runs with a fixed delay of `ASYNC_CONFIRMATION_SWEEP_INTERVAL` (default `5m`). It queries for flows whose `awaiting_async_since` is older than `ASYNC_CONFIRMATION_TIMEOUT` (default `90m`) and fails each one individually via `FlowService.failIfNotTerminal`, so a single database error does not abort the whole sweep.

## Business rules and constraints

- `EVENT_PUBLISHER_AWAIT_COMPLETION` controls only whether event-publishing stages wait for confirmation. It does **not** gate the `resource-result` consumer: the listener is registered unconditionally, outbound events always carry the publishing stage's UUID, and inbound results are always applied.
- With `EVENT_PUBLISHER_AWAIT_COMPLETION=false` (the default), publishing stages transition to `FINISHED` immediately on successful publish. A result that arrives afterwards therefore finds the stage already resolved and is ignored.
- When `EVENT_PUBLISHER_AWAIT_COMPLETION=true`, the capability, system-user, and scheduled-job publishers leave their stage `IN_PROGRESS` after publishing. The stage advances only when a matching `ResourceResultEvent` arrives. If no result arrives within `ASYNC_CONFIRMATION_TIMEOUT`, the sweeper fails the flow.
- A `resource-result` event is applied only if the resolved stage is still `IN_PROGRESS`. Events for stages already in a terminal status are dropped, which makes redelivery of the same result harmless.
- **`awaitingAsyncSince` anchor**: when the flow finalizer (`AbstractFlowFinalizer`) determines that async confirmations are still pending (`IN_PROGRESS` status), it stamps `awaiting_async_since` on the flow row rather than writing a terminal status. The `finishFlowIfNoActiveStages` query requires this anchor to be non-null: without it, no inbound result can complete the flow, which prevents a fast downstream response from finishing a flow whose remaining stages have not started yet.
- **Finalizer re-check**: `AbstractFlowFinalizer.onSuccess` registers an after-commit callback that calls `FlowCompletionService.completeIfNoActiveStages`. This handles the race where every confirmation had already arrived before the finalizer ran — without it, the finalizer's inline check and an in-flight result could each observe the other as pending and neither would complete the flow.
- `AbstractFlowFinalizer.afterFlowStatusUpdate()` is invoked regardless of whether the outcome is terminal or `IN_PROGRESS`, so application finalizers (entitle/revoke/upgrade record persistence) run immediately and do not wait for async completion.
- On a `SUCCESS` result: the stage transitions to `FINISHED`; the application flow transitions to `FINISHED` if it has no stage rows still `IN_PROGRESS` **and** its `awaiting_async_since` anchor is set; the top-level flow transitions to `FINISHED` under the same conditions. Note these checks consider only stage rows that already exist — rows are created as each stage starts, so stages that have not begun yet are not counted.
- On a `FAILURE` result: the stage transitions to `FAILED` with error details from `ResourceResultEvent.details` (or a default message if details are absent); the application flow transitions to `FAILED`; the top-level flow transitions to `FAILED`. Sibling stages are left at their current status.
- Each `flow_stage` row carries a unique `stage_id` UUID (populated by Liquibase migration for existing rows via `gen_random_uuid()`). This value is the correlation key for the entire feedback loop.

## Error behavior

- **Stage not found** by the UUID in the event: logged at `WARN`, event is dropped, no exception propagated to Kafka.
- **Stage already in a terminal status**: logged at `INFO`, event is dropped (duplicate or late delivery).
- **Missing `status`**: `ResourceResultEvent.status` is `@NotNull`; a null value fails `@Valid` before the service method body runs. The container's error handler retries up to 3 times (1 s interval) then publishes the record to the dead-letter topic.
- **Non-UUID `id`**: `UUID.fromString` throws `IllegalArgumentException`. `IllegalArgumentException` is registered as non-retryable on the container's error handler, so the record goes directly to the dead-letter topic without retries.
- **Dead-letter topic**: unprocessable records are published to `${ENV}.mgr-tenant-entitlements.resource-result.dlt` (same partition as the source record) by `DeadLetterPublishingRecoverer`.
- **Sweeper error**: if `FlowService.failIfNotTerminal` throws for a given flow, the error is caught and logged at `ERROR`; the sweep continues for the remaining flows.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `EVENT_PUBLISHER_AWAIT_COMPLETION` | `false` | When `true`, capability, system-user, and scheduled-job event-publishing stages leave their flow stage `IN_PROGRESS` after publishing and wait for a `resource-result` acknowledgment before finishing. Does not gate the consumer — inbound results are applied at either setting. |
| `ASYNC_CONFIRMATION_TIMEOUT` | `90m` | How long a flow may wait for async stage confirmations before the sweeper fails it. Bound to `application.async-confirmation.timeout`. |
| `ASYNC_CONFIRMATION_SWEEP_INTERVAL` | `5m` | Fixed delay between sweeper executions. Bound to `application.async-confirmation.sweep-interval`. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PARTITIONS` | `1` | Partition count for the `mgr-tenant-entitlements.resource-result` and `.dlt` topics created at startup. |
| `KAFKA_RESOURCE_RESULT_TOPIC_REPLICATION_FACTOR` | _(broker default)_ | Replication factor for those topics. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PATTERN` | `${ENV}.mgr-tenant-entitlements.resource-result` | Topic pattern for the `resource-result` Kafka listener. Supports regex. |
| `KAFKA_RESOURCE_RESULT_TOPIC_CONCURRENCY` | `1` | Number of concurrent consumers for the `resource-result` listener. |

## Dependencies and interactions

- Downstream services that emit `ResourceResultEvent` messages (e.g. mod-roles-keycloak publishing capability processing results). They receive the stage UUID via the `id` field of the inbound `ResourceEvent` and must echo it back as the `id` of their `ResourceResultEvent`.
- `folio-integration-kafka` library: provides the `ResourceEvent`, `ResourceResultEvent`, and `ResourceResultStatus` types. `ResourceResultEvent.status` carries a `@NotNull` constraint; `ResourceResultEvent.id` carries `@NotBlank`.
- `FlowCompletionService`: central coordinator for both success and failure completion paths, called by `ResourceResultEventService` and by `AbstractFlowFinalizer.onSuccess`.
- `AsyncConfirmationSweeper`: calls `FlowService.findStaleAsyncFlowIds` and `FlowService.failIfNotTerminal` to time out stuck flows.
- `FlowStageService`, `ApplicationFlowService`, and `FlowService`: used to apply cascading status transitions.
