-- The role catalogue is reference data, so it belongs in a migration rather than in
-- an ApplicationRunner: it must exist before the first request, be identical in every
-- environment, and be diffable in review.

INSERT INTO roles (name, description)
VALUES ('TENANT_ADMIN', 'Full control over one tenant: provisions users and assigns roles'),
       ('TENANT_USER', 'Ordinary member of a tenant')
ON CONFLICT (name) DO NOTHING;
