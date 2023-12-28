insert into entitlement(application_id, application_name, application_version, tenant_id) values
  ('folio-app1-1.0.0', 'folio-app1', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914'),
  ('folio-app3-3.0.0', 'folio-app3', '3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_module(module_id, tenant_id, application_id) values
  ('folio-module1-1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app1-1.0.0'),
  ('folio-module3-3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app3-3.0.0');

insert into application_dependency(application_id, application_name, application_version,
    parent_name, parent_version, tenant_id) values
  ('folio-app3-3.0.0', 'folio-app3', '3.0.0', 'folio-app1', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version, tenant_id,
    flow_id, type, status, started_at, finished_at) values
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'folio-app1-1.0.0', 'folio-app1', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
   '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00'),

  ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'folio-app3-3.0.0', 'folio-app3', '3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
   '9416654b-2a90-4185-9351-c280cd340ee5', 'ENTITLE', 'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00');

-- folio-app1-1.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 12:00:01', '2022-01-01 12:00:05'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'TenantLoader', 'FINISHED', '2022-01-01 12:00:06', '2022-01-01 12:00:10'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 12:00:11', '2022-01-01 12:00:15'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencySaver', 'FINISHED', '2022-01-01 12:00:16', '2022-01-01 12:00:20'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 12:00:21', '2022-01-01 12:00:25'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 12:00:26', '2022-01-01 12:00:30'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 12:00:31', '2022-01-01 12:00:35'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'FolioModuleInstaller', 'FINISHED', '2022-01-01 12:00:36', '2022-01-01 12:00:40'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 12:00:41', '2022-01-01 12:00:45'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 12:00:46', '2022-01-01 12:00:46');

-- folio-app3-3.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 12:00:01', '2022-01-01 12:00:05'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'TenantLoader', 'FINISHED', '2022-01-01 12:00:06', '2022-01-01 12:00:10'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 12:00:11', '2022-01-01 12:00:15'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDependencySaver', 'FINISHED', '2022-01-01 12:00:16', '2022-01-01 12:00:20'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 12:00:21', '2022-01-01 12:00:25'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 12:00:26', '2022-01-01 12:00:30'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 12:00:31', '2022-01-01 12:00:35'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'FolioModuleInstaller', 'FINISHED', '2022-01-01 12:00:36', '2022-01-01 12:00:40'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 12:00:41', '2022-01-01 12:00:45'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 12:00:46', '2022-01-01 12:00:46');
