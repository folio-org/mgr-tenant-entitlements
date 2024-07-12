INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('okapi-app-1.1.0', 'okapi-app', '1.1.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');

INSERT INTO entitlement_module(module_id, tenant_id, application_id)
VALUES ('okapi-module-1.1.0', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'okapi-app-1.1.0');

INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('56a99f07-b5b9-4fd5-bd04-e26b4ed25182', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE',
        'FINISHED', '2022-01-01 09:00:00', '2022-01-01 09:20:40'),
       ('f79cedce-4ec9-4b23-aaea-e759f34d4b8b', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'UPGRADE',
        'FINISHED', '2022-01-01 10:00:00', '2022-01-01 10:20:40');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at, finished_at)
VALUES ('22556baa-0644-489f-a7b3-8f854bbcfc8c', 'okapi-app-1.0.0', 'okapi-app', '1.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', '56a99f07-b5b9-4fd5-bd04-e26b4ed25182', 'ENTITLE', 'FINISHED',
        '2022-01-01 09:00:00', '2022-01-01 09:20:35'),
       ('66540821-4291-42b4-8752-ea674d03fbbf', 'okapi-app-1.1.0', 'okapi-app', '1.1.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', 'f79cedce-4ec9-4b23-aaea-e759f34d4b8b', 'UPGRADE', 'FINISHED',
        '2022-01-01 10:00:00', '2022-01-01 10:20:35');

