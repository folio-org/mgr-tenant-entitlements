INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('okapi-app3-3.0.0', 'okapi-app3', '3.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c'),
       ('okapi-app4-4.0.0', 'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c');

INSERT INTO application_dependency(application_id, application_name, application_version,
                                   parent_name, parent_version, tenant_id)
VALUES ('okapi-app3-3.0.0', 'okapi-app3', '3.0.0', 'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c');

INSERT INTO flow(flow_id, tenant_id, type, status, started_at, finished_at)
VALUES ('3d94cd49-0ede-4426-81dc-416ff7deb187', '82dec29a-927f-4a14-a9ea-dc616fd17a1c', 'ENTITLE',
        'FINISHED', '2022-01-01 09:00:00', '2022-01-01 09:20:50'),
       ('9416654b-2a90-4185-9351-c280cd340ee5', '82dec29a-927f-4a14-a9ea-dc616fd17a1c', 'ENTITLE',
        'FINISHED', '2022-01-02 09:00:00', '2022-01-02 09:20:50');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version, tenant_id,
                             flow_id, type, status, started_at, finished_at)
VALUES ('64f6b5ab-4894-45cf-b1b9-760c1c6b800b', 'okapi-app3-3.0.0', 'okapi-app3', '3.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c',
        '3d94cd49-0ede-4426-81dc-416ff7deb187', 'ENTITLE', 'FINISHED', '2022-01-01 09:00:05', '2022-01-01 09:20:45'),
       ('edb37a4b-3dc7-4478-8070-23bfaf96c0f1', 'okapi-app4-4.0.0', 'okapi-app4', '4.0.0', '82dec29a-927f-4a14-a9ea-dc616fd17a1c',
        '9416654b-2a90-4185-9351-c280cd340ee5', 'ENTITLE', 'FINISHED', '2022-01-02 09:00:05', '2022-01-02 09:20:45');
