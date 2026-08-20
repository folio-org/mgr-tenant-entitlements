---
feature_id: async-stage-result-tracking
title: Async Stage Result Tracking
updated: 2026-08-20
---

# Async Stage Result Tracking

## What it does

Allows event-publishing stages (capabilities, system-user, scheduled-job) to remain `IN_PROGRESS` after sending a Kafka event, and advance to `FINISHED` or `FAILED` only when the downstream consumer reports the outcome back via the `resource-result` Kafka topic. The service correlates inbound results to the originating stage using a UUID embedded as the `id` field of every outbound `ResourceEvent`. When the result arrives, the stage, the application flow, and the top-level flow are all advanced to their terminal statuses in a single transaction.

## Why it exists

Previously, publishing a Kafka event ended the publishing stage immediately as `FINISHED` regardless of whether the downstream system (e.g. mod-roles-keycloak processing capabilities) had actually completed its work. This made it impossible to track async outcomes and caused flows to be reported as finished before their real work was done (MGRENTITLE-158).

## Entry point(s)

**Inbound — Kafka consumer**

| Listener ID | Topic pattern | Group ID |
|-------------|--------------|----------|
| `resource-result-event-listener` | `KAFKA_RESOURCE_RESULT_TOPIC_PATTERN` (default `${ENV}.resource-result`) | `${ENV}-mgr-tenant-entitlements-resource-result-group` |

Processes `ResourceResultEvent` messages. The event's `id` field must be the UUID of an existing `flow_stage` row (`stage_id` column). Listener container factory: `kafkaListenerContainerFactory`.

**Outbound — stage UUID in published events**

Each capability, system-user, and scheduled-job Kafka event now carries the publishing stage's UUID as `ResourceEvent.id`. Downstream services use this value as the `id` field of the `ResourceResultEvent` they send back.

**REST — stage ID exposure**

The `id` field on `FlowStage` (returned by `GET /entitlement-flows/{flowId}?includeStages=true` and `GET /entitlement-flows/{flowId}/app-flows/{appFlowId}/stages`) exposes the same UUID, allowing callers to correlate stage records with async results.

## Business rules and constraints

- `EVENT_PUBLISHER_AWAIT_COMPLETION=false` (the default) leaves all existing behavior intact: event-publishing stages transition to `FINISHED` immediately on successful publish, and no `resource-result` consumer activity is required.
- When `EVENT_PUBLISHER_AWAIT_COMPLETION=true`, the capability, system-user, and scheduled-job publishers leave their stage `IN_PROGRESS` after publishing. The stage only advances when a matching `ResourceResultEvent` arrives.
- A `resource-result` event is processed only if the resolved stage is `IN_PROGRESS`. Events for stages already in a terminal status are silently dropped (idempotent — handles duplicate delivery).
- On a `SUCCESS` result: the stage transitions to `FINISHED`; the application flow transitions to `FINISHED` if no other stages for that flow are still `IN_PROGRESS`; the top-level flow transitions to `FINISHED` if no application flows are still `IN_PROGRESS`.
- On a `FAILURE` result: the stage transitions to `FAILED` with error details from `ResourceResultEvent.details`; the application flow transitions to `FAILED`; the top-level flow transitions to `FAILED`.
- The flow finalizer (`AbstractFlowFinalizer`) will not stamp `finishedAt` on the flow while `FlowFinalizerStageAwareStatusProvider` reports any sibling stages still `IN_PROGRESS`. Entitlement/revoke/upgrade records are always persisted by the finalizer's `afterFlowStatusUpdate()` hook regardless of pending async stages.
- Each `flow_stage` row carries a unique `stage_id` UUID (populated by Liquibase migration for existing rows via `gen_random_uuid()`). This value is the correlation key for the entire feedback loop.

## Error behavior

- **Stage not found** by the UUID in the event: logged at `INFO`, event is dropped, no exception propagated to Kafka.
- **Stage already in a terminal status**: logged at `INFO`, event is dropped (duplicate or late delivery).
- **Unknown `ResourceResultStatus` value**: throws `IllegalStateException`; the listener will raise a consumer error and the message will be handled according to the listener container's error handler (no explicit dead-letter configuration is added by this feature).

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `EVENT_PUBLISHER_AWAIT_COMPLETION` | `false` | When `true`, capability, system-user, and scheduled-job event-publishing stages leave their flow stage `IN_PROGRESS` after publishing and wait for a `resource-result` acknowledgment before finishing. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PARTITIONS` | `1` | Partition count for the `resource-result` topic created at startup. |
| `KAFKA_RESOURCE_RESULT_TOPIC_REPLICATION_FACTOR` | _(broker default)_ | Replication factor for the `resource-result` topic. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PATTERN` | `${ENV}.resource-result` | Topic pattern for the `resource-result` Kafka listener. Supports regex. |
| `KAFKA_RESOURCE_RESULT_TOPIC_CONCURRENCY` | `1` | Number of concurrent consumers for the `resource-result` listener. |

## Dependencies and interactions

- Downstream services that emit `ResourceResultEvent` messages (e.g. mod-roles-keycloak publishing capability processing results). They receive the stage UUID via the `id` field of the inbound `ResourceEvent` and must echo it back as the `id` of their `ResourceResultEvent`.
- `folio-integration-kafka` library: provides the `ResourceEvent`, `ResourceResultEvent`, and `ResourceResultStatus` types.
- `FlowStageService`, `ApplicationFlowService`, and `FlowService`: used by `ResourceResultEventService` to apply the cascading status transitions within a single transaction.
