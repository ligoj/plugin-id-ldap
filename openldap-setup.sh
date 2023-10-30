#! /bin/bash

slappasswd ligoj-admin

export  LDAP_ADMIN_PASSWORD="_admin_secret_"
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

bash -c '
cd /Users/fabdouglas/git/ligoj-plugins/plugin-id-ldap && \
ldapadd -c -x -D "cn=Manager,dc=sample,dc=com" -w "$LDAP_ADMIN_PASSWORD" -H ldap://localhost:1389 -f  ../plugin-id-ldap-embedded/src/main/resources/export/base.ldif && \
ldapsearch -c -x -D "cn=Manager,dc=sample,dc=com" -w "$LDAP_ADMIN_PASSWORD" -H ldap://localhost:1389 -b "dc=sample,dc=com"
'

# Create 100K users
NB_USER=100000
source ./.secrets.sh
rm -f bench.ldiff
for ((i=1;i<=$NB_USER;i++)); do 
    echo "Create user $i"
    echo "
dn: uid=cli${i},ou=department1,ou=internal,ou=people,dc=sample,dc=com
cn: Cli${i} Name${i}
sn: Name${i}
givenName: Cli${i}
uid: cli${i}
mail: cli${i}.name@sample.com
objectClass: inetOrgPerson
userPassword: ligoj-user
" >> bench.ldiff
done
ldapadd -c -x -D "cn=Manager,dc=sample,dc=com" -w "$LDAP_ADMIN_PASSWORD" -H ldap://localhost:1389 -f bench.ldiff
