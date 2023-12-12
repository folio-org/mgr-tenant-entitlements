insert into entitlement(application_id, application_name, application_version, tenant_id) values
  ('okapi-app-1.0.0', 'okapi-app', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_module(module_id, tenant_id, application_id)
values ('okapi-module-1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'okapi-app-1.0.0');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version,
    tenant_id, flow_id, type, status, started_at, finished_at) values
  ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'okapi-app-1.0.0', 'okapi-app', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
  '56a99f07-b5b9-4fd5-bd04-e26b4ed25182', 'ENTITLE', 'FINISHED', '2022-01-01 09:00:00', '2022-01-01 09:01:00');

-- test-app-1.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 09:00:01', '2022-01-01 09:00:05'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'TenantLoader', 'FINISHED', '2022-01-01 09:00:06', '2022-01-01 09:00:10'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 09:00:11', '2022-01-01 09:00:15'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 09:00:16', '2022-01-01 09:00:20'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 09:00:21', '2022-01-01 09:00:25'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 09:00:26', '2022-01-01 09:00:30'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'OkapiModuleInstaller', 'FINISHED', '2022-01-01 09:00:31', '2022-01-01 09:00:35'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 09:00:36', '2022-01-01 09:00:40'),
       ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 09:00:41', '2022-01-01 09:00:46');
