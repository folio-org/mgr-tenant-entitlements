insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version,
    tenant_id, flow_id, type, status, started_at, finished_at) values
  ('39409745-399b-4f93-8af9-133840ad04a9', 'test-app-1.0.0', 'test-app', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
  '0fcbe8aa-1745-432b-ac54-2fe222564675', 'REVOKE', 'FINISHED', '2022-01-01 09:00:00', '2022-01-01 09:01:00');

-- test-app-1.0.0 entitlement stages
insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at)
values ('39409745-399b-4f93-8af9-133840ad04a9', 'EntitlementFlowInitializer', 'FINISHED', '2022-01-01 09:00:01', '2022-01-01 09:00:05'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'TenantLoader', 'FINISHED', '2022-01-01 09:00:06', '2022-01-01 09:00:10'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'ApplicationDescriptorLoader', 'FINISHED', '2022-01-01 09:00:11', '2022-01-01 09:00:15'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'ApplicationDependencyValidator', 'FINISHED', '2022-01-01 09:00:16', '2022-01-01 09:00:20'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'ApplicationDiscoveryValidator', 'FINISHED', '2022-01-01 09:00:21', '2022-01-01 09:00:25'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'KongGatewayRouteRegistrar', 'FINISHED', '2022-01-01 09:00:26', '2022-01-01 09:00:30'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'OkapiModuleInstaller', 'FINISHED', '2022-01-01 09:00:31', '2022-01-01 09:00:35'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'EntitlementEventPublisher', 'FINISHED', '2022-01-01 09:00:36', '2022-01-01 09:00:40'),
       ('39409745-399b-4f93-8af9-133840ad04a9', 'EntitlementFlowFinalizer', 'FINISHED', '2022-01-01 09:00:41', '2022-01-01 09:00:46');
