# Étape 1 : compilation avec Java 17 et Maven
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests

# Étape 2 : exécution avec Tomcat 10.1 et Java 17
FROM tomcat:10.1-jdk17-temurin

ENV MBOA_UPLOADS_DIR=/var/lib/mboa-style/uploads

RUN mkdir -p /var/lib/mboa-style/uploads

# Déployer BAZAAR à la racine du domaine
COPY --from=build /build/target/mboa-style.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 10000

# Render fournit le port dans la variable PORT.
# Adapter le connecteur Tomcat avant de démarrer le serveur.
CMD ["sh", "-c", "sed -i 's/port=\"8080\"/port=\"'\"${PORT:-10000}\"'\"/' /usr/local/tomcat/conf/server.xml && exec catalina.sh run"]
