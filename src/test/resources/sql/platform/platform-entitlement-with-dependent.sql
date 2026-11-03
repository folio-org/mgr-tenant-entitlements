INSERT INTO platform_entitlement(application_name, application_id, application_version)
VALUES ('platform-app', 'platform-app-1.0.0', '1.0.0');

INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('folio-app3-3.0.0', 'folio-app3', '3.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

INSERT INTO application_dependency(application_id, application_name, application_version,
                                   parent_name, parent_version, tenant_id)
VALUES ('folio-app3-3.0.0', 'folio-app3', '3.0.0', 'platform-app', '^1.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914');
