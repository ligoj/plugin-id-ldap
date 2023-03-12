## :link: Ligoj Identity LDAP plugin [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-id-ldap/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-id-ldap)

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

# Plugin parameters

| Parameter                               | Value                                  | Note                                                                                                                                  |                     
|-----------------------------------------|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| service:id:ldap:base-dn                 | <empty>                                | Base DN of all DN. Should be empty for an easiest fine grained configuration.                                                         |
| service:id:ldap:companies-dn            | ou=people,dc=sample,dc=com             | DN within the people DN  where the companies owning real people are stored.                                                           |                             
| service:id:ldap:company-class           | organizationalUnit                     | LDAP object class of companies. Is also a filter for search.                                                                          |                            
| service:id:ldap:company-pattern         | [^,]+,ou=([^,]+),.*                    | Pattern extracting the company string name from a DN of an user.                                                                      |                            
| service:id:ldap:department-attribute    | employeeNumber                         | LDAP attribute name for the department value. Use a value compatible withe the LDAP schema.                                           |                            
| service:id:ldap:groups-dn               | ou=groups,dc=sample,dc=com             | DN of groups.                                                                                                                         |                                  
| service:id:ldap:groups-member-attribute | uniqueMember                           | LDAP group's attribute name referring to its members' DN.                                                                             |                                  
| service:id:ldap:groups-class            | groupOfUniqueNames                     | LDAP object class of groups. Is also a filter for search.                                                                             |                                  
| service:id:ldap:local-id-attribute      | employeeID                             | LDAP attribute name for the local employee number.                                                                                    |                                         
| service:id:ldap:locked-attribute        | employeeType                           | LDAP attribute name for the locked status of an user.                                                                                 |                                     
| service:id:ldap:locked-value            | LOCKED                                 | LDAP attribute valued of locked user.                                                                                                 |                               
| service:id:ldap:password                | <required>                             | Clear administrator password. This value is encrypted in database.                                                                    |                    
| service:id:ldap:people-class            | inetOrgPerson                          | LDAP object class of users. Is also a filter for search.                                                                              |                               
| service:id:ldap:people-dn               | ou=people,dc=sample,dc=com             | Base DN of the people. This DN is used as primary search location for users.                                                          |                          
| service:id:ldap:people-internal-dn      | ou=internal,ou=people,dc=sample,dc=com | DN within the people DN to separate internal (writable) users from the other. (not yet fully implemented).                            |            
| service:id:ldap:quarantine-dn           | ou=quarantine,dc=sample,dc=com         | DN outside the people DN. Receive the users moved from their source without deleting them.                                            |       
| service:id:ldap:self-search             | false                                  | When `true`, during authentication, the user's DN is build with a search using it's credential. Otherwise admin credentials are used. |       
| service:id:ldap:uid-attribute           | uid                                    | LDAP attribute name user identifier.                                                                                                  |    
| service:id:ldap:url                     | ldap://localhot:389                    | This  value is encrypted in database.                                                                                                 |            
| service:id:ldap:user-dn                 | cn=Manager,dc=sample,dc=com            | DN of administrator.                                                                                                                  |