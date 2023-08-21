FROM clojure:temurin-20-alpine AS build
COPY deps.edn .
RUN clojure -P -M:dev
COPY . .
RUN clojure -M:dev -m rinha.build

FROM eclipse-temurin:20-alpine
COPY --from=build target/rinha.jar .
CMD java -jar rinha.jar
