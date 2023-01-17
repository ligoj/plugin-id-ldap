#! /bin/bash

# See: http://blog.facilelogin.com/2012/05/setting-up-openldap-under-mac-os-x.html
# See: https://github.com/IntersectAustralia/acdata/wiki/Setting-up-OpenLDAP
brew install berkeley-db@4 openldap  
brew upgrade
slappasswd
cp /private/etc/openldap/slapd.conf.default /private/etc/openldap/slapd.conf
vi /private/etc/openldap/slapd.conf

include     /private/etc/openldap/schema/core.schema
include     /private/etc/openldap/schema/cosine.schema
include     /private/etc/openldap/schema/nis.schema
include     /private/etc/openldap/schema/inetorgperson.schema
modulepath  /usr/libexec/openldap
moduleload  back_bdb.la
...
suffix          "dc=example,dc=com"
rootdn          "cn=Manager,dc=example,dc=com"
rootpw      {SSHA}....

sudo /usr/libexec/slapd -d3


ldapadd -x -D "cn=Manager,dc=sample,dc=com" -W -H ldap:// -f /Users/fabdouglas/git/ligoj-plugins/plugin-id-ldap-embedded/src/main/resources/export/base.ldif