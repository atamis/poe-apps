(ns poe-apps.store-manager.foo
  (:require
   [yada.yada :as yada]
   [integrant.core :as ig]
   [clojure.data.json :as json]

   [clojure.java.io :as io]))


(def files (->> (io/file "cache/")
                 file-seq
                 (filter #(.endsWith (.getName %) ".json"))
                 (sort-by #(.getName %))
                 (map
                  (fn [file]
                    (let [name (.getName file)]
                      (with-open [f (io/reader file)]
                        (json/read f :key-fn keyword)))))
                 (into [])
                 ))

(defmethod ig/init-key ::cached
  [_ x]
  (println x)
  (yada/resource
   {:parameters {:path {:id Long}}
    :methods {:get {:produces #{"application/json"
                                "application/edn;q=0.9"}
                    :response (fn [ctx]
                                (nth files (get-in ctx [:parameters :path :id])))}}}
   )
  )

(def tabs (->> files first :tabs))

(defmethod ig/init-key ::tabs-resource
  [_ x]
  (yada/as-resource tabs))

(defn string-resource
  [x]
  (yada/as-resource x))

(defmethod ig/init-key ::string
  [_ x]
  (string-resource x))
