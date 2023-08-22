(ns rinha.postgres
  (:require
   [honey.sql :as honey]
   [honey.sql.helpers :as hh]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc.rs]))

(def ^:private default-batch-size 108)

(defn- columns [entities]
  (->> entities (mapcat keys) (into #{})))

(defn- build-insert-sql [table entities]
  (let [cols (columns entities)]
    [(-> (hh/insert-into (keyword table))
         (hh/values [(zipmap cols (repeat 0))])
         (honey/format {:quoted true})
         first)
     (map (apply juxt cols) entities)]))

(defn create-many!
  ([ds table entities] (create-many! {:return-data? false} ds table entities))
  ([{:keys [return-data?] :as opts}
    ds table entities]
   (let [entities' entities
         [sql values] (build-insert-sql table entities')]
     (jdbc/with-transaction [tx ds]
       (try
         (jdbc/execute-batch! tx sql values
           (cond-> {:builder-fn jdbc.rs/as-unqualified-maps
                    :batch-size default-batch-size}
             ;; Tricks to make next-jdbc return the result set instead of counts:
             return-data? (assoc :return-keys true, :return-generated-keys true)))
         (catch Exception e
           (throw (ex-info
                    (str "Failed to insert: " (ex-message e))
                    {:table table :cnt-entities (count entities)}
                    e))))))))
