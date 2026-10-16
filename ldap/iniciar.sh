#!/bin/sh
# Levanta un OpenLDAP de laboratorio en ldap://localhost:3890 con los
# usuarios de usuarios.ldif. Solo lectura anónima; nada se escribe por red.
# Requiere: brew install openldap
set -e
OL=$(brew --prefix openldap)
SCHEMA=$(brew --prefix)/etc/openldap/schema
DIR="$HOME/CampusSeguro/ldap"
LDIF="$(cd "$(dirname "$0")" && pwd)/usuarios.ldif"

# Detener uno anterior
[ -f "$DIR/slapd.pid" ] && kill "$(cat "$DIR/slapd.pid")" 2>/dev/null && sleep 1
rm -rf "$DIR" && mkdir -p "$DIR/datos"

cat > "$DIR/slapd.conf" <<CONF
include  $SCHEMA/core.schema
include  $SCHEMA/cosine.schema
include  $SCHEMA/inetorgperson.schema
pidfile  $DIR/slapd.pid
access to * by * read
database mdb
suffix   "dc=campus,dc=local"
directory $DIR/datos
CONF

"$OL/sbin/slapadd" -f "$DIR/slapd.conf" -l "$LDIF"
"$OL/libexec/slapd" -f "$DIR/slapd.conf" -h ldap://localhost:3890/
echo "OpenLDAP listo en ldap://localhost:3890 (base dc=campus,dc=local)"
