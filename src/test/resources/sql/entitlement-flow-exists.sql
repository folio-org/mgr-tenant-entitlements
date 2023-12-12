insert into entitlement(application_id, tenant_id)
values ('folio-app1-1.1.0', '176317cb-c3aa-45be-a60b-47e73737eb55');

insert into entitlement_flow(entitlement_flow_id, application_id, application_name, application_version, tenant_id, flow_id, type, status, started_at, finished_at)
values
  ('bed2dc08-17f4-45f1-82da-84ffc65c5825', 'folio-app1-1.1.0', 'folio-app1', '1.1.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
  'def173a0-7b4c-4f45-b66c-5fe4aa7c8f98', 'ENTITLE', 'FINISHED', '2023-01-01 12:01:00', '2023-01-01 12:01:59');
