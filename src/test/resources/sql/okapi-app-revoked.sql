INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('0fcbe8aa-1745-432b-ac54-2fe222564675', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE',
        'FINISHED', '2024-04-13 12:00:00', '2024-04-13 12:15:50');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at, finished_at)
VALUES ('39409745-399b-4f93-8af9-133840ad04a9', 'test-app-1.0.0', 'test-app', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914',
       '0fcbe8aa-1745-432b-ac54-2fe222564675', 'REVOKE', 'FINISHED', '2024-04-13 12:00:05', '2024-04-13 12:15:45');
