INSERT INTO flow(flow_id, tenant_id, type, status, started_at)
VALUES ('aa000000-0000-0000-0000-000000000001', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE', 'IN_PROGRESS', now());

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at)
VALUES ('bb000000-0000-0000-0000-000000000001', 'test-app-1.0.0', 'test-app', '1.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', 'aa000000-0000-0000-0000-000000000001', 'ENTITLE', 'IN_PROGRESS', now());

INSERT INTO flow_stage(flow_id, stage, status, started_at, stage_id)
VALUES ('bb000000-0000-0000-0000-000000000001', 'CapabilitiesModuleEventPublisher',
        'IN_PROGRESS', now(), 'cc000000-0000-0000-0000-000000000001'),
       ('bb000000-0000-0000-0000-000000000001', 'SystemUserModuleEventPublisher',
        'IN_PROGRESS', now(), 'cc000000-0000-0000-0000-000000000002');
