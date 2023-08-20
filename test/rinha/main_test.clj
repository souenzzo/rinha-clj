(ns rinha.main-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]]
            [next.jdbc :as jdbc]
            [rinha.main :as rinha]))


;; start a psql:
;; docker run -e POSTGRES_PASSWORD=postgres --rm -p 5432:5432 postgres:alpine

(deftest hello
  (let [{::rinha/keys [conn]
         ::http/keys  [service-fn]} (-> {::rinha/jdbc-url "jdbc:postgres://localhost:5432/postgres?user=postgres&password=postgres"}
                                      rinha/default-interceptors
                                      http/dev-interceptors
                                      http/create-servlet)]
    (try
      (jdbc/execute! conn ["DROP TABLE stack"])
      (catch Throwable _ex :ok))
    (try
      (jdbc/execute! conn ["DROP TABLE pessoa"])
      (catch Throwable _ex :ok))
    (try
      (jdbc/execute! conn ["
CREATE TABLE pessoa (
  apelido TEXT UNIQUE NOT NULL PRIMARY KEY,
  nome TEXT NOT NULL,
  nascimento DATE NOT NULL
);"])
      (catch Throwable _ex :ok))
    (try
      (jdbc/execute! conn ["
CREATE TABLE stack (
  ident TEXT NOT NULL,
  pessoa TEXT NOT NULL REFERENCES pessoa(apelido),
  PRIMARY KEY(ident, pessoa)
);"])
      (catch Throwable _ex :ok))
    (is (= 0
          (-> service-fn
            (response-for :get "/contagem-pessoas")
            :body
            json/parse-string
            #_(doto clojure.pprint/pprint))))
    (is (= 202
          (-> service-fn
            (response-for :post "/pessoas"
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string {:apelido    "josé"
                                           :nome       "José Roberto"
                                           :nascimento "2000-10-01"
                                           :stack      ["C#" "Node" "Oracle"]}))
            :status
            #_json/parse-string
            (doto clojure.pprint/pprint))))
    (is (= {:apelido    "josé"
            :nascimento "2000-09-30"
            :nome       "José Roberto"
            :stack      ["C#" "Node" "Oracle"]}
          (-> service-fn
            (response-for :get "/pessoas/josé")
            :body
            (json/parse-string true)
            #_(doto clojure.pprint/pprint))))
    (is (= 1
          (-> service-fn
            (response-for :get "/contagem-pessoas")
            :body
            json/parse-string
            #_(doto clojure.pprint/pprint))))))
