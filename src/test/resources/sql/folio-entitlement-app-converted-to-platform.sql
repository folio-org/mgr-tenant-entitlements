-- The application was entitled for the tenant while it was still tenant-scoped.
-- Its descriptor in mgr-applications is platform-scoped now, so the tenant keeps an entitlement of a platform application.
INSERT INTO entitlement(application_id, application_name, application_version, tenant_id)
VALUES ('tenant-to-platform-app-1.0.0', 'tenant-to-platform-app', '1.0.0', '6ad28dae-7c02-4f89-9320-153c55bf1914');
