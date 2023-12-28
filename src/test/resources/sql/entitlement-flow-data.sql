insert into entitlement(application_id, application_name, application_version, tenant_id)
values ('test-app1-1.0.0', 'test-app1', '1.0.0', '176317cb-c3aa-45be-a60b-47e73737eb55'),
       ('test-app2-1.0.0', 'test-app2', '1.0.0', '176317cb-c3aa-45be-a60b-47e73737eb55');

insert into application_dependency(application_id, application_name, application_version,
  parent_name, parent_version, tenant_id)
values
  ('test-app2-1.0.0', 'test-app2', '1.0.0', 'test-app1', '1.0.0', '176317cb-c3aa-45be-a60b-47e73737eb55'),
  ('test-app4-1.0.0', 'test-app4', '1.0.0', 'test-app3', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version,
    tenant_id, flow_id, type, status, started_at, finished_at)
values
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'test-app1-1.0.0', 'test-app1', '1.0.0', '176317cb-c3aa-45be-a60b-47e73737eb55',
  'def173a0-7b4c-4f45-b66c-5fe4aa7c8f98', 'ENTITLE', 'FINISHED', '2023-01-01 12:01:00', '2023-01-01 12:01:59'),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'test-app2-1.0.0', 'test-app2', '1.0.0', '176317cb-c3aa-45be-a60b-47e73737eb55',
  'def173a0-7b4c-4f45-b66c-5fe4aa7c8f98', 'ENTITLE', 'FINISHED', '2023-01-01 12:02:00', '2023-01-01 12:02:59'),

  -- failed entitlement attempt for app3 and app4
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'test-app3-1.0.0', 'test-app3', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED', '2023-01-01 13:01:00', '2023-01-01 13:01:59'),
  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'test-app4-1.0.0', 'test-app4', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FAILED', '2023-01-01 13:02:00', '2023-01-01 13:02:59'),

  -- positive entitlement attempt for app3 and app4
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'test-app3-1.0.0', 'test-app3', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  'd3610151-68ff-495d-8b0c-cec170ed07c7', 'ENTITLE', 'FINISHED', '2023-01-01 13:05:00', '2023-01-01 13:05:59'),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'test-app4-1.0.0', 'test-app4', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  'd3610151-68ff-495d-8b0c-cec170ed07c7', 'ENTITLE', 'FINISHED', '2023-01-01 13:06:00', '2023-01-01 12:06:59'),

  -- positive revoke entitlement attempt for app3 and app4
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'test-app3-1.0.0', 'test-app3', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  'c7c51e7e-c475-433b-ae66-4457ee4b3fd3', 'REVOKE', 'FINISHED', '2023-01-02 12:00:00', '2023-01-01 12:00:59'),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'test-app4-1.0.0', 'test-app4', '1.0.0', 'ae1aabc8-c329-476b-901e-991c0dda8426',
  'c7c51e7e-c475-433b-ae66-4457ee4b3fd3', 'REVOKE', 'FINISHED', '2023-01-02 12:01:00', '2023-01-01 12:00:59');

insert into entitlement_stage(entitlement_flow_id, stage, status, started_at, finished_at, error_message) values
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 12:01:01', '2023-01-01 12:01:05', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'TenantLoader', 'FINISHED', '2023-01-01 12:01:06', '2023-01-01 12:01:10', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 12:01:11', '2023-01-01 12:01:15', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 12:01:16', '2023-01-01 12:01:20', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 12:01:21', '2023-01-01 12:01:25', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 12:01:26', '2023-01-01 12:01:30', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 12:01:31', '2023-01-01 12:01:35', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 12:01:36', '2023-01-01 12:01:40', null),
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 12:01:41', '2023-01-01 12:01:46', null),

  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 12:02:01', '2023-01-01 12:02:05', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'TenantLoader', 'FINISHED', '2023-01-01 12:02:06', '2023-01-01 12:02:10', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 12:02:11', '2023-01-01 12:02:15', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 12:02:16', '2023-01-01 12:02:20', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 12:02:21', '2023-01-01 12:02:25', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 12:02:26', '2023-01-01 12:02:30', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 12:02:31', '2023-01-01 12:02:35', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 12:02:36', '2023-01-01 12:02:40', null),
  ('e9b839d8-140f-4ef2-b9db-38c289d220d6', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 12:02:41', '2023-01-01 12:02:46', null),

  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 13:01:01', '2023-01-01 13:01:05', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'TenantLoader', 'FINISHED', '2023-01-01 13:01:06', '2023-01-01 13:01:10', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 13:01:11', '2023-01-01 13:01:15', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 13:01:16', '2023-01-01 13:01:20', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 13:01:21', '2023-01-01 13:01:25', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 13:01:26', '2023-01-01 13:01:30', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 13:01:31', '2023-01-01 13:01:35', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 13:01:36', '2023-01-01 13:01:40', null),
  ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 13:01:41', '2023-01-01 13:01:46', null),

  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 13:02:01', '2023-01-01 13:02:05', null),
  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'TenantLoader', 'FINISHED', '2023-01-01 13:02:06', '2023-01-01 13:02:10', null),
  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 13:02:11', '2023-01-01 13:02:15', null),
  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 13:02:16', '2023-01-01 13:02:20', null),
  ('4fdbb687-8e80-46b4-8328-a3ede141aa08', 'ApplicationDiscoveryValidator', 'FAILED', '2023-01-01 13:02:21', '2023-01-01 13:02:25', 'Application discovery information is not defined for [mod-foo-1.0.0]'),

  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 13:05:01', '2023-01-01 13:05:05', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'TenantLoader', 'FINISHED', '2023-01-01 13:05:06', '2023-01-01 13:05:10', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 13:05:11', '2023-01-01 13:05:15', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 13:05:16', '2023-01-01 13:05:20', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 13:05:21', '2023-01-01 13:05:25', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 13:05:26', '2023-01-01 13:05:30', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 13:05:31', '2023-01-01 13:05:35', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 13:05:36', '2023-01-01 13:05:40', null),
  ('d682652a-35e2-41c8-9908-d441901d7b0c', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 13:05:41', '2023-01-01 13:05:46', null),

  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 13:06:01', '2023-01-01 13:06:05', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'TenantLoader', 'FINISHED', '2023-01-01 13:06:06', '2023-01-01 13:06:10', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 13:06:11', '2023-01-01 13:06:15', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 13:06:16', '2023-01-01 13:06:20', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 13:06:21', '2023-01-01 13:06:25', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 13:06:26', '2023-01-01 13:06:30', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 13:06:31', '2023-01-01 13:06:35', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 13:06:36', '2023-01-01 13:06:40', null),
  ('59bd05a6-4cb6-4f19-9a8f-c67e6fe27706', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 13:06:41', '2023-01-01 13:06:46', null),

  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-01 12:00:01', '2023-01-01 12:00:05', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'TenantLoader', 'FINISHED', '2023-01-01 12:00:06', '2023-01-01 12:00:10', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-01 12:00:11', '2023-01-01 12:00:15', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-01 12:00:16', '2023-01-01 12:00:20', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-01 12:00:21', '2023-01-01 12:00:25', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-01 12:00:26', '2023-01-01 12:00:30', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'OkapiModuleInstaller', 'FINISHED', '2023-01-01 12:00:31', '2023-01-01 12:00:35', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'EntitlementEventPublisher', 'FINISHED', '2023-01-01 12:00:36', '2023-01-01 12:00:40', null),
  ('aada83c2-26a4-46db-858a-ac56729e93fe', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-01 12:00:41', '2023-01-01 12:00:46', null),

  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'EntitlementFlowInitializer', 'FINISHED', '2023-01-02 12:01:01', '2023-01-02 12:01:05', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'TenantLoader', 'FINISHED', '2023-01-02 12:01:06', '2023-01-02 12:01:10', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'ApplicationDescriptorLoader', 'FINISHED', '2023-01-02 12:01:11', '2023-01-02 12:01:15', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'ApplicationDependencyValidator', 'FINISHED', '2023-01-02 12:01:16', '2023-01-02 12:01:20', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'ApplicationDiscoveryValidator', 'FINISHED', '2023-01-02 12:01:21', '2023-01-02 12:01:25', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'KongGatewayRouteRegistrar', 'FINISHED', '2023-01-02 12:01:26', '2023-01-02 12:01:30', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'OkapiModuleInstaller', 'FINISHED', '2023-01-02 12:01:31', '2023-01-02 12:01:35', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'EntitlementEventPublisher', 'FINISHED', '2023-01-02 12:01:36', '2023-01-02 12:01:40', null),
  ('bedb449f-fbc5-4605-b2f6-2f0b9368845d', 'EntitlementFlowFinalizer', 'FINISHED', '2023-01-02 12:01:41', '2023-01-02 12:01:46', null);
