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

