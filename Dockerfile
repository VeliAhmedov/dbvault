# temurin eclipse is base image based on, jre is enough as project already run on compiled jar so no need compiler
FROM eclipse-temurin:25-jre
#exe commands while building image
# 1. apt-get update (refresh pckgs index)
# 2. apt-get install (instal linux pckgs), -y (autoconfirm), --no-install-recommends (avoid instal unimportant recomended pckgs)
# ca-certificates (provide certificate for HTTPS), curl (download)
# 3. -d /usr/share/postgresql-common/pgdg (create dir to hold DB's repo sign key)
# 4. download DB's signing key, -fsSL (-f, -s, -S, -L) respectively ( fail on HTTP error, silent mode, show error in silent mode, follow redirects)
# 5. /etc/os-release and echo (add PostgreSQL pckgs repo)
# 6. apt-get update \ (update pckgs ifno again, include PostgreSQL's repo)
# 7. apt-get install (install PostgreSQL 18 client tools)
# 8. rm -rf /var/lib/apt/lists/* ( remove downloaded pckg index to redice img size)
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && install -d /usr/share/postgresql-common/pgdg \
 && curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc \
 && . /etc/os-release \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client-18 \
 && rm -rf /var/lib/apt/lists/*

# copy my compile JAR file (this project) into image, maven prodced target/dbvault-0.1.0.jar copied to /app/dbvault.jar
COPY target/dbvault-*.jar /app/dbvault.jar
# /work as default work dir inside container
WORKDIR /work
# cmds to run when container starts -> docker run --rm dbvault --help (java -jar /app/dbvault.jar --help)
ENTRYPOINT ["java", "-jar", "/app/dbvault.jar"]