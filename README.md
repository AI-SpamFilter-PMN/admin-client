# admin-client

Admin dashboard for the AI spam-filter project. A self-contained Java SE app
(embedded Tomcat, no external servlet container needed) for moderating
traffic on the shared Neon (Postgres) database: subscribers, message/call
history, system logs, and the blocklist/whitelist.

Independent of `sms-client` - its own login, its own port - but reads the
same Neon database.

## Running

```
mvn package
java -Dserver.port=8082 -Ddb.url="postgresql://user:password@host/db?sslmode=require" -jar target/admin-client.jar
```

`db.url` should never be committed - pass it via `-Ddb.url=...` or the
`DB_URL` environment variable at launch.

## Access

Login is by email against the shared `users` table - an account needs
`role = ROLE_ADMIN` or `ROLE_ESCALATION` to sign in here. There's no
separate admin account to configure.
