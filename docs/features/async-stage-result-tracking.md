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

- `EVENT_PUBLISHER_AWAIT_COMPLETION` controls only whether event-publishing stages wait for confirmation. It does **not** gate the `resource-result` consumer: the listener is registered unconditionally, outbound events always carry the publishing stage's UUID, and inbound results are always applied.
- With `EVENT_PUBLISHER_AWAIT_COMPLETION=false` (the default), publishing stages transition to `FINISHED` immediately on successful publish. A result that arrives afterwards therefore finds the stage already resolved and is ignored.
- When `EVENT_PUBLISHER_AWAIT_COMPLETION=true`, the capability, system-user, and scheduled-job publishers leave their stage `IN_PROGRESS` after publishing. The stage only advances when a matching `ResourceResultEvent` arrives — there is currently no timeout or sweeper, so a result that never arrives leaves the stage and its flow `IN_PROGRESS` indefinitely.
- A `resource-result` event is applied only if the resolved stage is still `IN_PROGRESS`. Events for stages already in a terminal status are dropped, which makes redelivery of the same result harmless.
- On a `SUCCESS` result: the stage transitions to `FINISHED`; the application flow transitions to `FINISHED` if it has no stage rows still `IN_PROGRESS`; the top-level flow transitions to `FINISHED` if it has neither in-progress stage rows nor in-progress application flows. Note these checks consider only stage rows that **already exist** — rows are created as each stage starts, so stages that have not begun yet are not counted.
- On a `FAILURE` result: the stage transitions to `FAILED` with error details from `ResourceResultEvent.details`; the application flow transitions to `FAILED`; the top-level flow transitions to `FAILED`. Sibling stages of the failed flow are left at their current status.
- While async confirmations are pending, the flow finalizer (`AbstractFlowFinalizer`) leaves the flow row untouched — it does not stamp `finishedAt` — but still runs `afterFlowStatusUpdate()`, so entitlement/revoke/upgrade records are persisted. If the flow row has already reached a terminal status by the time the finalizer runs, the finalizer skips both the status write and those side effects.
- Each `flow_stage` row carries a unique `stage_id` UUID (populated by Liquibase migration for existing rows via `gen_random_uuid()`). This value is the correlation key for the entire feedback loop.

## Error behavior

- **Stage not found** by the UUID in the event: logged at `INFO`, event is dropped, no exception propagated to Kafka.
- **Stage already in a terminal status**: logged at `INFO`, event is dropped (duplicate or late delivery).
- **Missing or null `status`**: `ResourceResultEvent` declares no constraint on `status`, so validation admits the event and the handler lookup throws `NullPointerException` (`Map.of(...).get(null)`), not the `IllegalStateException` the surrounding guard is written for.
- **Malformed `id`**: `id` is validated as `@NotBlank` only, so a value that is not a UUID throws `IllegalArgumentException` from `UUID.fromString`.
- In both of the above cases the exception propagates to the listener container. No `ErrorHandlingDeserializer` and no dead-letter topic are configured by this feature, so handling falls back to the container default: the record is retried and then committed with only a log entry, and the result it carried is lost.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `EVENT_PUBLISHER_AWAIT_COMPLETION` | `false` | When `true`, capability, system-user, and scheduled-job event-publishing stages leave their flow stage `IN_PROGRESS` after publishing and wait for a `resource-result` acknowledgment before finishing. Does not gate the consumer — inbound results are applied at either setting. Note the `@Value` fallbacks in the publisher classes currently default to `true`; the `false` above is the effective default because `application.yml` binds the keys explicitly. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PARTITIONS` | `1` | Partition count for the `resource-result` topic created at startup. |
| `KAFKA_RESOURCE_RESULT_TOPIC_REPLICATION_FACTOR` | _(broker default)_ | Replication factor for the `resource-result` topic. |
| `KAFKA_RESOURCE_RESULT_TOPIC_PATTERN` | `${ENV}.resource-result` | Topic pattern for the `resource-result` Kafka listener. Supports regex. |
| `KAFKA_RESOURCE_RESULT_TOPIC_CONCURRENCY` | `1` | Number of concurrent consumers for the `resource-result` listener. |

## Dependencies and interactions

- Downstream services that emit `ResourceResultEvent` messages (e.g. mod-roles-keycloak publishing capability processing results). They receive the stage UUID via the `id` field of the inbound `ResourceEvent` and must echo it back as the `id` of their `ResourceResultEvent`.
- `folio-integration-kafka` library: provides the `ResourceEvent`, `ResourceResultEvent`, and `ResourceResultStatus` types.
- `FlowStageService`, `ApplicationFlowService`, and `FlowService`: used by `ResourceResultEventService` to apply the cascading status transitions within a single transaction.
