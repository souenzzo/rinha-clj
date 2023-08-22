postgres:
	docker run -e POSTGRES_PASSWORD=postgres --rm -p 5432:5432 postgres:alpine

repl:
	clojure -A:dev
