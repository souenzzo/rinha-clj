(ns rinha.main-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]]
            [next.jdbc :as jdbc]
            [rinha.main :as rinha]))


;; start a psql:
;; docker run -e POSTGRES_PASSWORD=postgres --rm -p 5432:5432 postgres:alpine

(defn setup-database
  []
  (let [{::rinha/keys [conn]
         ::http/keys  [service-fn]} (-> {::rinha/jdbc-url "jdbc:postgres://localhost:5432/postgres?user=postgres&password=postgres"}
                                        rinha/default-interceptors
                                        http/dev-interceptors
                                        http/create-servlet)]
    (jdbc/execute! conn ["DROP TABLE IF EXISTS stack"])
    (jdbc/execute! conn ["DROP TABLE IF EXISTS pessoa"])
    (jdbc/execute! conn [(slurp (io/resource "schema.sql"))])
    service-fn))

(deftest hello
  (let [service-fn (setup-database)]
    (is (= 0
           (-> service-fn
               (response-for :get "/contagem-pessoas")
               :body
               json/parse-string
               #_(doto clojure.pprint/pprint))))
    (is (= "/pessoas/josé"
           (-> service-fn
               (response-for :post "/pessoas"
                             :headers {"Content-Type" "application/json"}
                             :body (json/generate-string {:apelido    "josé"
                                                          :nome       "José Roberto"
                                                          :nascimento "2000-10-01"
                                                          :stack      ["C#" "Node" "Oracle"]}))
               :headers
               (get "Location")
               #_(doto clojure.pprint/pprint))))
    (is (= "/pessoas/ana"
           (-> service-fn
               (response-for :post "/pessoas"
                             :headers {"Content-Type" "application/json"}
                             :body (json/generate-string {:apelido    "ana"
                                                          :nascimento "1985-09-23"
                                                          :nome       "Ana Barbosa"
                                                          :stack      ["Node" "Postgres"]}))
               :headers
               (get "Location")
               #_(doto clojure.pprint/pprint))))

    (is (= {:apelido    "josé"
            :nascimento "2000-09-30"
            :nome       "José Roberto"
            :stack      ["C#" "Node" "Oracle"]}
           (-> service-fn
               (response-for :get "/pessoas/josé")
               :body
               (json/parse-string true)
               #_(doto clojure.pprint/pprint))))
    (is (= [{:apelido    "josé",
             :nome       "José Roberto",
             :nascimento "2000-09-30",
             :stack      ["C#" "Node" "Oracle"]}
            {:apelido    "ana",
             :nome       "Ana Barbosa",
             :nascimento "1985-09-22",
             :stack      ["Node" "Postgres"]}]
           (-> service-fn
               (response-for :get "/pessoas?t=node")
               :body
               (json/parse-string true)
               #_(doto clojure.pprint/pprint))))
    (is (= [{:apelido    "josé",
             :nome       "José Roberto",
             :nascimento "2000-09-30",
             :stack      ["C#" "Node" "Oracle"]}]
           (-> service-fn
               (response-for :get "/pessoas?t=berto")
               :body
               (json/parse-string true)
               #_(doto clojure.pprint/pprint))))
    (is (= []
           (-> service-fn
               (response-for :get "/pessoas?t=Python")
               :body
               (json/parse-string true)
               #_(doto clojure.pprint/pprint))))
    (is (= 400
           (-> service-fn
               (response-for :get "/pessoas")
               :status
               #_(doto clojure.pprint/pprint))))
    (is (= 2
           (-> service-fn
               (response-for :get "/contagem-pessoas")
               :body
               json/parse-string
               #_(doto clojure.pprint/pprint))))))
