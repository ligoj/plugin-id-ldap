#! /bin/bash

slappasswd ligoj-admin

export  LDAP_ADMIN_PASSWORD="u-=*HKum+Nwz?yYk4Jej"
podman rm openldap
podman run --name openldap \
  --detach \
  --env LDAP_ADMIN_USERNAME="Manager" \
  --env LDAP_ADMIN_PASSWORD="$LDAP_ADMIN_PASSWORD" \
  --env LDAP_ROOT="dc=sample,dc=com" \
  --env LDAP_USERS=customuser \
  --env LDAP_PASSWORDS=custompassword \
  -p 1389:1389 \
  bitnami/openldap:latest

ldapadd -c -x -D "cn=Manager,dc=sample,dc=com" -w "$LDAP_ADMIN_PASSWORD" -H ldap://localhost:1389 -f  ../plugin-id-ldap-embedded/src/main/resources/export/base.ldif
ldapsearch -c -x -D "cn=Manager,dc=sample,dc=com" -w "$LDAP_ADMIN_PASSWORD" -H ldap://localhost:1389 -b "dc=sample,dc=com"

podman rm sonarqube
podman rm jenkins

git clone https://github.com/SonarSource/docker-sonarqube
podman build --rm=true --platform linux/arm64 --tag=sonarqube .
podman run -d --name sonarqube -p 9000:9000 localhost/sonarqube:latest
podman start sonarqube


git clone https://github.com/sonatype/docker-nexus3
podman build --rm=true --platform linux/arm64 --tag=sonatype/nexus3 .
podman run -d -p 8081:8081 --name nexus sonatype/nexus3
podman exec nexus cat /nexus-data/admin.password
podman start nexus

# brew services stop postgresql
# brew services restart jenkins-lts
# brew services stop jenkins-lts
/opt/homebrew/opt/openjdk@17/bin/java -Dmail.smtp.starttls.enable=true -jar /opt/homebrew/opt/jenkins-lts/libexec/jenkins.war --httpListenAddress=0.0.0.0 --httpPort=9190
cat /Users/fabdouglas/.jenkins/secrets/initialAdminPassword
cd07bb686bf34746a4f885b7bd91f2e9
fdaugan+jenkins-admin@kloudy.io
API-token: 114e133e456fb65f18c3e0d64c26702112
JENKINS_HOME='/Users/fabdouglas/.jenkins'
http://localhost:9190/


http://discourse.kloudy.io:32080/
https://github.com/jonmbake/discourse-ldap-auth
https://github.com/omniauth/omniauth-ldap -> pas de filtre de class, pas de group (https://github.com/omniauth/omniauth-ldap/pull/43)

# Workflow (proposition)

[common]  Ligoj: add/remove user from group
[project] Jenkins script: add forum -> update permission
[common]  Discourse: need custom plugin (metadata) -> https://meta.discourse.org/t/metadata-for-groups/142394/5   
[common]  Discourse sync memberships LDAP -> Discourse
  Soit:
    https://meta.discourse.org/t/sync-woocommerce-memberships-with-discourse-groups/118485
    https://gitlab.cern.ch/cgamoudi/discourse-groups-sync (CERN)
  Soit:
    Ligoj/Schedule (like Gitlab): update members of forum/group according to LDAP group membership. Cron 10min?


Intégration SAML plus élaborée, compatible avec Keycloak
https://forum.chatons.org/t/synchroniser-les-groupes-entre-keycloak-et-discourse/4487

