# Team database viewer (CloudBeaver)

The production PostgreSQL database is available to the team through CloudBeaver:

- URL: <https://sy-725ad61798e54aca9aca4901becbef0b.ecs.ap-northeast-2.on.aws/db/>
- Shared login: `viewer`
- Connection: `Melody Bubble Production (Read Only)`
- AWS Region: `ap-northeast-2`

## Sign in and browse data

1. Open the URL and sign in as `viewer`.
2. Get the password from AWS Secrets Manager secret `melody-bubble/cloudbeaver`, key `CB_VIEWER_PASSWORD`.
3. Expand `Shared` > `Melody Bubble Production (Read Only)`.
4. If the database authentication dialog appears, leave the optional SSL certificate fields empty and click `LOGIN`. The database credential is already saved server-side.
5. Expand `Databases` > `postgres` > `Schemas` > `public` > `Tables`.
6. Double-click a table and select `Data` to see the current rows. Use the refresh action to reload live data.

An AWS-authorized administrator can retrieve the shared login password with:

```bash
aws secretsmanager get-secret-value \
  --region ap-northeast-2 \
  --secret-id melody-bubble/cloudbeaver \
  --query SecretString \
  --output text | jq -r '.CB_VIEWER_PASSWORD'
```

Do not post the password in Git, chat, or issue trackers. Share it through the team's approved secret-sharing channel.

## Administrator access

Use the `dbadmin` CloudBeaver account only when a production data or schema change is required:

1. Get `CB_DB_ADMIN_PASSWORD` from the `melody-bubble/cloudbeaver` secret.
2. Sign in to the same CloudBeaver URL as `dbadmin`.
3. Open `Melody Bubble Production (Admin)` for editable access.

The administrator connection uses PostgreSQL role `team_db_admin`. It can select, insert, update, delete, and truncate current `public` tables; use and update sequences; execute routines; and create objects in the `public` schema. Matching default privileges are configured for future objects created by `melody`.

It is intentionally not an RDS superuser: it cannot create roles or databases, manage AWS infrastructure, or bypass RDS-level controls. Existing application-owned objects may also require the owning `melody` role for ownership-only operations such as some `ALTER` or `DROP` statements.

## Read-only enforcement

CloudBeaver connects to RDS as PostgreSQL role `team_viewer`. The role has:

- `default_transaction_read_only=on`
- `CONNECT` on database `postgres`
- `USAGE` on schema `public`
- `SELECT` on current tables and sequences
- default `SELECT` privileges for future tables and sequences owned by the application role
- no schema `CREATE` privilege

The restriction is enforced by PostgreSQL, not only by the CloudBeaver UI. UI actions that attempt to add, edit, or delete data will be rejected by the database.

## Access and credentials

- Anonymous CloudBeaver access is disabled.
- Shared CloudBeaver account: `viewer` / secret key `CB_VIEWER_PASSWORD`
- Editable CloudBeaver administrator: `dbadmin` / secret key `CB_DB_ADMIN_PASSWORD`
- CloudBeaver administrator: `teamadmin` / secret key `CB_ADMIN_PASSWORD`
- Database read-only role: `team_viewer` / secret key `DB_VIEWER_PASSWORD`
- Database editable administrator role: `team_db_admin` / secret key `DB_ADMIN_PASSWORD`
- The `viewer` CloudBeaver account has only the default `user` team and direct access to the production read-only connection.
- The `dbadmin` account has the `admin` and `user` teams and direct access to the production administrator connection.

## AWS resources

- ECS service: `default/cloudbeaver`
- ECS task definition: `melody-cloudbeaver:1`
- Container image: `dbeaver/cloudbeaver:26.0.5`
- Log group: `/aws/ecs/default/cloudbeaver`
- EFS file system: `fs-0b10b986641bb96ca`
- EFS access point: `fsap-0a6cfe09611e7f887`
- ALB: `ecs-express-gateway-alb-d0b82a7d`
- ALB path rule: `/db` and `/db/*`
- Target group: `melody-cloudbeaver`
- Secrets Manager secret: `melody-bubble/cloudbeaver`
- DB administrator bootstrap task definition: `melody-db-admin-bootstrap:3`

CloudBeaver workspace data is persisted on encrypted EFS at `/opt/cloudbeaver/workspace`.

## Operational checks

```bash
aws ecs describe-services \
  --region ap-northeast-2 \
  --cluster default \
  --services cloudbeaver

aws logs tail /aws/ecs/default/cloudbeaver \
  --region ap-northeast-2 \
  --since 30m

curl -I https://sy-725ad61798e54aca9aca4901becbef0b.ecs.ap-northeast-2.on.aws/db/
```
