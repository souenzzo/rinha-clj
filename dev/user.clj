(ns user
  (:require [clojure.tools.nrepl.server :as nrepl]))

(defonce nrepl-server (nrepl/start-server))
(spit "./.nrepl-port" (:port nrepl-server))
