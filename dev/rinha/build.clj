(ns rinha.build
  (:require [clojure.java.io :as io]
            [clojure.tools.build.api :as b]))

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})
        target (io/file "target")
        class-dir (io/file target "classes")
        uber-file (io/file target "rinha.jar")]
    (b/delete {:path (str target)})
    (b/write-pom {:class-dir (str class-dir)
                  :lib       'rinha/app
                  :version   "1.0.0"
                  :basis     basis})
    (b/copy-dir {:src-dirs   ["resources"]
                 :target-dir (str class-dir)})
    (b/compile-clj {:basis     basis
                    :class-dir (str class-dir)})
    (b/uber {:class-dir (str class-dir)
             :main      'rinha.main
             :uber-file (str uber-file)
             :basis     basis})))
