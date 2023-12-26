insert into entitlement(application_id, tenant_id)
values ('folio-app2-2.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_module(module_id, tenant_id, application_id)
values ('folio-module2-2.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app2-2.0.0');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version,
    tenant_id, flow_id, type, status, started_at, finished_at)
values ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'folio-app2-2.0.0', 'folio-app2', '2.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00');

insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 12:00:01', '2022-01-01 12:00:05'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'TenantLoader', 'FINISHED', '2022-01-01 12:00:06', '2022-01-01 12:00:10'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 12:00:11', '2022-01-01 12:00:15'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencySaver', 'FINISHED', '2022-01-01 12:00:16', '2022-01-01 12:00:20'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 12:00:21', '2022-01-01 12:00:25'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 12:00:26', '2022-01-01 12:00:30'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 12:00:31', '2022-01-01 12:00:35'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'OkapiModuleInstaller', 'FINISHED', '2022-01-01 12:00:36', '2022-01-01 12:00:40'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 12:00:41', '2022-01-01 12:00:45'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 12:00:46', '2022-01-01 12:00:46');
