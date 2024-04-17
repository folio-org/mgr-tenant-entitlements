INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('folio-app1-1.0.0', 'folio-app1', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914'),
       ('folio-app3-3.0.0', 'folio-app3', '3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

INSERT INTO entitlement_module(module_id, tenant_id, application_id)
VALUES ('folio-module1-1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app1-1.0.0'),
       ('folio-module3-3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app3-3.0.0');

INSERT INTO application_dependency(application_id, application_name, application_version,
                                   parent_name, parent_version, tenant_id)
VALUES ('folio-app3-3.0.0', 'folio-app3', '3.0.0', 'folio-app1', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('3d94cd49-0ede-4426-81dc-416ff7deb187', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00'),
       ('9416654b-2a90-4185-9351-c280cd340ee5', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version, tenant_id,
                             flow_id, type, status, started_at, finished_at)
VALUES ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'folio-app1-1.0.0', 'folio-app1', '1.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'folio-app3-3.0.0', 'folio-app3', '3.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '9416654b-2a90-4185-9351-c280cd340ee5', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00');
