insert into entitlement(application_id, tenant_id)
values ('folio-app9-9.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

insert into entitlement_module(module_id, tenant_id, application_id)
values ('folio-module9-9.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app9-9.0.0');

INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('99435b8d-e20e-4c1a-8833-5d8452d42c64', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE',
        'FINISHED', '2022-01-01 12:00:00', '2022-01-01 12:01:00');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at, finished_at)
VALUES ('3c6ad02d-4fa4-4117-9838-c6857bddf0e7', 'folio-app9-9.0.0', 'folio-app9', '9.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '99435b8d-e20e-4c1a-8833-5d8452d42c64', 'ENTITLE', 'FINISHED',
        '2022-01-01 12:00:00', '2022-01-01 12:01:00');
