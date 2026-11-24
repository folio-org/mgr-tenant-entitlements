INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('folio-app10-10.0.0', 'folio-app10', '10.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

INSERT INTO entitlement_module(module_id, tenant_id, application_id)
VALUES ('folio-module10-10.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'folio-app10-10.0.0');
