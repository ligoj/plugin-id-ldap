## :link: Ligoj Identity LDAP plugin ![Maven Central](https://img.shields.io/maven-central/v/org.ligoj.plugin/plugin-id-ldap)

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.ligoj.plugin%3Aplugin-id-ldap&metric=coverage)](https://sonarcloud.io/dashboard?id=org.ligoj.plugin%3Aplugin-id-ldap)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?metric=alert_status&project=org.ligoj.plugin:plugin-id-ldap)](https://sonarcloud.io/dashboard/index/org.ligoj.plugin:plugin-id-ldap)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/abf810c094e44c0691f71174c707d6ed)](https://www.codacy.com/gh/ligoj/plugin-id-ldap?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ligoj/plugin-id-ldap&amp;utm_campaign=Badge_Grade)
[![CodeFactor](https://www.codefactor.io/repository/github/ligoj/plugin-id-ldap/badge)](https://www.codefactor.io/repository/github/ligoj/plugin-id-ldap)
[![License](http://img.shields.io/:license-mit-blue.svg)](http://fabdouglas.mit-license.org/)

[Ligoj](https://github.com/ligoj/ligoj) LDAP identity plugin, and
extending [Identity plugin](https://github.com/ligoj/plugin-id)
Provides the following features :

- LDAP synchronization and caching
- User activity contribution

Requires [IAM Node plugin](https://github.com/ligoj/plugin-iam-node) to select the LDAP node used for authentication.

# Cache resources

Cache resources TTL can be overridden from Configuration API with `cache.$name.ttl` property. A cache can also expire at fixed times with `cache.$name.cron`, a Spring CRON expression in the time zone of the server, such as `0 0 3 * * *` for every day at 03:00: it takes precedence over the TTL.

| Cache           | Default TTL (S) | Note                                                  |
|-----------------|-----------------|-------------------------------------------------------|
| user-details    | Eternal (0)     | Assumes there is no LDAP update for other place       |
| customers       | Eternal (0)     | Assumes there is no LDAP group update for other place |
| customers-by-id | Eternal (0)     | Assumes there is no LDAP group update for other place |
| id-ldap-data    | 1 day (86400)   | Period of the full read of the LDAP users, groups and companies, see below |

## Database copy and restart

Each full read of LDAP is also saved in the database, through the copy shared by the identity plugins (plugin-id): the shared cache tables (`LIGOJ_CACHE_*`), whose user table holds every attribute read (DN, all mails, password presence, lock state, department, local identifier, custom attributes), and the state of the copy (`LIGOJ_ID_CACHE_COPY`: node, time of the full read, fingerprint of the node configuration). Ligoj's own changes (user creation, update, deletion, lock, isolation, password, memberships) keep this copy current.

After a restart of the API, the users, groups and companies are rebuilt from this copy, without reading LDAP, until the next full read is due: at "last full read + TTL" (`cache.id-ldap-data.ttl`), or at the next time of `cache.id-ldap-data.cron` when it is set, whatever the restarts in between. With `cache.id-ldap-data.cron`, the full reads also run at these times, in the background: with `0 0 3 * * *`, LDAP is read every night at 03:00, and nobody waits for it during the day. A full read happens instead when:

- there is no copy yet, or a previous full read failed before saving its state;
- the node configuration changed (another node, server, base DN, object class, attribute, custom attribute names);
- the shared tables were reset by another identity provider or another plugin version since;
- the next full read is due;
- the `id-ldap-data` cache is invalidated from the cache administration page or with `ligoj cache invalidate`.

The log tells which path was taken. A restart from the copy logs `Rebuilt identity data of <node> from the database copy in ..., without reading the server: users: ..., groups: ..., companies: ...; last full read ... ago, next one at ..., in ...`. A full read logs `Full read required: <reason>`, then `Complete refresh of <node> took ...: users: ..., groups: ..., companies: ...; next one at ..., in ...`.

# Plugin parameters

| Parameter                                | Value                                  | Note                                                                                                                                   |                     
|------------------------------------------|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| service:id:ldap:base-dn                  | <empty>                                | Base DN of all DN. Should be empty for an easiest fine grained configuration.                                                          |
| service:id:ldap:companies-dn             | ou=people,dc=sample,dc=com             | DN within the people DN  where the companies owning real people are stored.                                                            |                             
| service:id:ldap:companies-class          | organizationalUnit                     | LDAP object classes of companies for search. Comma or space separated values.                                                          |                            
| service:id:ldap:companies-class-create   | <empty>                                | LDAP object classes of companies for the creation. Comma or space separated values. When empty, use the first of search classes.       |                            
| service:id:ldap:company-pattern          | [^,]+,ou=([^,]+),.*                    | Pattern extracting the company string name from a DN of an user.                                                                       |                            
| service:id:ldap:department-attribute     | employeeNumber                         | LDAP attribute name for the department value. Use a value compatible withe the LDAP schema.                                            |                            
| service:id:ldap:groups-dn                | ou=groups,dc=sample,dc=com             | DN of groups.                                                                                                                          |                                  
| service:id:ldap:groups-member-attribute  | uniqueMember                           | LDAP group's attribute name referring to its members' DN.                                                                              |                                  
| service:id:ldap:groups-class             | groupOfUniqueNames                     | LDAP object classes of groups for search. Comma or space separated values.                                                             |                                  
| service:id:ldap:groups-class-create      | <empty>                                | LDAP object classes of groups for the creation. Comma or space separated values. When empty, use the first of search classes.          |                                  
| service:id:ldap:local-id-attribute       | employeeID                             | LDAP attribute name for the local employee number.                                                                                     |                                         
| service:id:ldap:locked-attribute         | employeeType                           | LDAP attribute name for the locked status of an user.                                                                                  |                                     
| service:id:ldap:locked-value             | LOCKED                                 | LDAP attribute valued of locked user.                                                                                                  |                               
| service:id:ldap:login-attributes         | uid,mail                               | Accepted authentication LDAP attributes. Comma or space separated values. Ignored when `service:id:ldap:self-search` is `false`.       |    
| service:id:ldap:password                 | <required>                             | Clear administrator password. This value is encrypted in database.                                                                     |                    
| service:id:ldap:people-class             | inetOrgPerson                          | LDAP object classes of users for search. Comma or space separated values.                                                              |                               
| service:id:ldap:people-class-create      | <empty>                                | LDAP object classes of users for the creation. Comma or space separated values. When empty, use the first of search classes.           |                               
| service:id:ldap:people-custom-attributes | -                                      | **Removed**, replaced by the identity service parameter `service:id:people-custom-attributes`. A value stored before the upgrade is still read while the service parameter is empty. |
| service:id:ldap:people-dn                | ou=people,dc=sample,dc=com             | Base DN of the people. This DN is used as primary search location for users.                                                           |                          
| service:id:ldap:people-internal-dn       | ou=internal,ou=people,dc=sample,dc=com | DN within the people DN to separate internal (writable) users from the other. (not yet fully implemented).                             |            
| service:id:ldap:quarantine-dn            | ou=quarantine,dc=sample,dc=com         | DN outside the people DN. Receive the users moved from their source without deleting them.                                             |       
| service:id:ldap:self-search              | false                                  | When `true`, at authentication time, the admin credentials are used search and filter the user, and then the credentials are verified. |       
|                                          |                                        | When `false`, the user's credentials are checked without search. Supports only `service:id:ldap:login-attributes` = `uid,mail`.        |       
| service:id:ldap:uid-attribute            | uid                                    | LDAP attribute name user identifier.                                                                                                   |    
| service:id:ldap:url                      | ldap://localhost:389                   | One or several comma separated URLs. The first one is the primary, the next ones are for failover. Encrypted in database.              |            
| service:id:ldap:user-dn                  | cn=Manager,dc=sample,dc=com            | DN of administrator.                                                                                                                   |

> Node parameters are read once and kept in the `id-configuration` cache, and the directory data in the `id-ldap-data` cache (refreshed on its own after `cache.id-ldap-data.ttl`, or at the times of `cache.id-ldap-data.cron`). After changing node parameters, invalidate both caches from the cache administration page or with `ligoj cache invalidate`, or restart the API.