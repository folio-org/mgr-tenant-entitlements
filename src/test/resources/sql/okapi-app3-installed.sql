insert into entitlement(application_id, application_name, application_version, tenant_id) values
  ('okapi-app3-3.0.0', 'okapi-app3', '3.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c'),
  ('okapi-app4-4.0.0', 'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c');

insert into application_dependency(application_id, application_name, application_version,
    parent_name, parent_version, tenant_id) values
  ('okapi-app3-3.0.0', 'okapi-app3', '3.0.0',
   'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version,
    tenant_id, flow_id, type, status, started_at, finished_at) values

  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'okapi-app3-3.0.0', 'okapi-app3', '3.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c',
   '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00'),

  ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'okapi-app4-4.0.0', 'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c',
   '9416654b-2a90-4185-9351-c280cd340ee5', 'ENTITLE', 'FINISHED', '2022-01-02 12:00:00', '2022-01-02 12:01:00');

-- test-app-3.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 12:00:01', '2022-01-01 12:00:05'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'TenantLoader', 'FINISHED', '2022-01-01 12:00:06', '2022-01-01 12:00:10'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 12:00:11', '2022-01-01 12:00:15'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 12:00:16', '2022-01-01 12:00:20'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 12:00:21', '2022-01-01 12:00:25'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 12:00:26', '2022-01-01 12:00:30'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'OkapiModuleInstaller', 'FINISHED', '2022-01-01 12:00:31', '2022-01-01 12:00:35'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 12:00:36', '2022-01-01 12:00:40'),
       ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 12:00:41', '2022-01-01 12:00:46');

-- test-app-4.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-02 12:00:01', '2022-01-01 12:00:05'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'TenantLoader', 'FINISHED', '2022-01-02 12:00:06', '2022-01-01 12:00:10'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-02 12:00:11', '2022-01-01 12:00:15'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-02 12:00:16', '2022-01-01 12:00:20'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-02 12:00:21', '2022-01-01 12:00:25'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-02 12:00:26', '2022-01-01 12:00:30'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'OkapiModuleInstaller', 'FINISHED', '2022-01-02 12:00:31', '2022-01-01 12:00:35'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementEventPublisher', 'FINISHED', '2022-01-02 12:00:36', '2022-01-01 12:00:40'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-02 12:00:41', '2022-01-01 12:00:46');
