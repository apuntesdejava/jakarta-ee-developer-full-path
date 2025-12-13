FROM payara/server-full:7.2025.2
# 1. Copiar la App
COPY target/project-tracker.war $DEPLOY_DIR

# 2. Copiar el Script de configuración
COPY post-boot-commands.asadmin $POSTBOOT_COMMANDS

# -----------------------------------------------------------
# 3. DESCARGA AUTOMÁTICA DEL DRIVER JDBC
# -----------------------------------------------------------

# Cambiamos a root para tener permisos de escritura y descarga
USER root

# Definimos la versión que queremos (para cambiarla fácil en el futuro)
ENV PG_VERSION=42.7.8

# Usamos ADD para bajar el JAR directo de Maven Central a la carpeta de librerías
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_VERSION}/postgresql-${PG_VERSION}.jar \
    /opt/payara/appserver/glassfish/domains/domain1/lib/postgresql.jar

# IMPORTANTE: Al bajarlo con ADD, el archivo queda como propiedad de 'root'.
# Payara corre como usuario 'payara', así que debemos cambiar el dueño
# para asegurarnos de que el servidor pueda leerlo sin problemas.
RUN chown payara:payara /opt/payara/appserver/glassfish/domains/domain1/lib/postgresql.jar

# Volvemos al usuario payara para que el contenedor corra de forma segura
USER payara

# -----------------------------------------------------------

EXPOSE 8080 4848