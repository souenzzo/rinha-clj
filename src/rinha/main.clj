(ns rinha.main
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.instant :as instant]
   [io.pedestal.http :as http]
   [io.pedestal.http.body-params :as body-params]
   [io.pedestal.http.route :as route]
   [io.pedestal.interceptor :as interceptor]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [rinha.postgres :as postgres]))

(defn criar
  [{::keys [conn] :keys [json-params]}]
  (let [{:keys [apelido nome nascimento stack]} json-params]
    (jdbc/execute! conn ["INSERT INTO pessoa (apelido, nome, nascimento) VALUES (?, ?, ?)"
                         apelido nome (instant/read-instant-timestamp nascimento)])
    (postgres/create-many! conn "stack" stack)
    {:headers {"Location" (route/url-for ::consultar :params {:id apelido})}
     :status  201}))


(defn consultar
  [{:keys  [path-params]
    ::keys [conn]}]
  (let [{:keys [id]} path-params]
    {:body   (-> conn
               (jdbc/execute! ["SELECT apelido, nascimento, nome FROM pessoa WHERE apelido = ?" id])
               first
               (update :nascimento str)
               (assoc :stack (map :ident (jdbc/execute! conn ["SELECT ident FROM stack WHERE pessoa = ? ORDER BY ident" id])))
               json/generate-string)
     :status 200}))

(def query-de-busca
  "
  SELECT apelido, nome, nascimento
 FROM pessoa
 WHERE (apelido ILIKE ?)
    OR (nome ILIKE ?)
    OR (apelido IN (SELECT pessoa
                    FROM stack
                    WHERE ident ILIKE ?));
 ")

(defn buscar
  [{::keys [conn]
    :keys  [query-params]}]
  (let [{:keys [t]} query-params
        busca (str "%" t "%")]
    (if (= busca "%%")
      {:status 400}
      {:body   (-> (for [{:keys [apelido nascimento]
                          :as   pessoa} (jdbc/execute! conn [query-de-busca busca busca busca])]
                     (assoc pessoa :nascimento (str nascimento)
                                   :stack (map :ident (jdbc/execute! conn ["SELECT ident FROM stack WHERE pessoa = ? ORDER BY ident" apelido]))))
                 json/generate-string)
       :status 200})))


(defn contagem-pessoas
  [{::keys [conn]}]
  {:body   (-> conn
             (jdbc/execute! ["SELECT COUNT(apelido) FROM pessoa"])
             first
             :count
             str)
   :status 200})

(defn with-context
  [env]
  (update env
    ::http/interceptors (fn [interceptors]
                          (into [(interceptor/interceptor {:name  ::with-context
                                                           :enter (fn [ctx]
                                                                    (update ctx :request (partial merge env)))})]
                            interceptors))))

(defn default-interceptors
  [{::keys [jdbc-url]
    :as    service-map}]
  (let [conn (-> {:jdbcUrl jdbc-url}
               jdbc/get-connection
               (jdbc/with-options {:builder-fn rs/as-unqualified-maps}))]
    (-> service-map
      (assoc ::conn conn
             ::http/routes `#{["/pessoas" :post ~[(body-params/body-params) `criar]]
                              ["/pessoas/:id" :get consultar]
                              ["/pessoas" :get buscar]
                              ["/contagem-pessoas" :get contagem-pessoas]})
      http/default-interceptors
      with-context)))


(defn -main
  [& _]
  (-> {::jdbc-url   (System/getProperty "rinha.jdbc-url"
                      "jdbc:postgres://localhost:5432/postgres?user=postgres&password=postgres")
       ::http/port  (Long/getLong "rinha.http.port" 80)
       ::http/join? false
       ::http/type  :jetty}
    default-interceptors
    http/dev-interceptors
    http/create-server
    http/start))
