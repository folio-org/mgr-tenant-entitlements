# Module Features

This module provides the following features:

| Feature | Description |
|---------|-------------|
| [API Gateway Route Management](features/api-gateway-route-management.md) | API Gateway services and routes are automatically created, updated, and deleted as modules are entitled, upgraded, and revoked, keeping gateway state consistent with entitlement state. |
| [Entitlement Execution Timeout](features/entitlement-execution-timeout.md) | Synchronous entitlement operations that exceed the configured execution timeout end in a terminal `failed` state and return `400` with a flow identifier, instead of hanging indefinitely. |
| [Async Stage Result Tracking](features/async-stage-result-tracking.md) | Event-publishing stages can remain `IN_PROGRESS` after sending a Kafka event and advance to a terminal status only when the downstream service reports back via the `resource-result` topic. |
