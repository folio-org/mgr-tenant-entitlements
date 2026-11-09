INSERT INTO platform_entitlement(application_name, application_id, application_version)
VALUES ('platform-base', 'platform-base-1.0.0', '1.0.0'),
       ('platform-app', 'platform-app-1.0.0', '1.0.0');

INSERT INTO platform_application_dependency(application_id, application_name, application_version,
                                            parent_name, parent_version)
VALUES ('platform-app-1.0.0', 'platform-app', '1.0.0', 'platform-base', '^1.0.0');
