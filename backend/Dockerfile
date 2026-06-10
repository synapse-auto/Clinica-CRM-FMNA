# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copiar os arquivos necessários para o build via Gradle
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

# Fazer o download das dependências offline para otimização do cache
RUN ./gradlew dependencies --no-daemon

# Copiar o código fonte e rodar o build
COPY src src
RUN ./gradlew build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Adicionar um usuário sem privilégios de root por segurança
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copiar apenas o JAR construído do stage 1
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
