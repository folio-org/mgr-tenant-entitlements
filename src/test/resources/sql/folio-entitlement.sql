insert into entitlement(application_id, tenant_id)
values ('folio-app2-2.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_module(module_id, tenant_id, application_id)
values ('folio-module2-2.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app2-2.0.0');

INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('3d94cd49-0ede-4426-81dc-416ff7deb187', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE',
        'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at, finished_at)
VALUES ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'folio-app2-2.0.0', 'folio-app2', '2.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00');
