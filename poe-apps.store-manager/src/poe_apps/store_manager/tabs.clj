(ns poe-apps.store-manager.tabs
  (:require
   [yada.yada :as yada]
   [integrant.core :as ig]
   [clojure.data.json :as json]

   [clojure.java.io :as io]
   [clojure.string :as string]))

(def files (->> (io/resource "cache/")
                io/file
                file-seq
                (filter #(.endsWith (.getName %) ".json"))
                (sort-by #(Integer/parseInt
                           (second
                            (string/split (.getName %)
                                          #"_"))))
                (map
                 (fn [file]
                   (let [name (.getName file)]
                     (with-open [f (io/reader file)]
                       (json/read f :key-fn keyword)))))
                (into [])))

(defmethod ig/init-key ::cached
  [_ x]
  (println x)
  (yada/resource
   {:parameters {:path {:id Long}}
    :methods {:get {:produces #{"application/json"
                                "application/edn;q=0.9"}
                    :response (fn [ctx]
                                (nth files (get-in ctx [:parameters :path :id])))}}}))

(def tabs (->> files first :tabs))

(defmethod ig/init-key ::tabs-resource
  [_ x]
  (yada/as-resource tabs))

(def items (->> files (mapcat :items)))

(defmethod ig/init-key ::items-resource
  [_ _]
  (yada/resource
   {:parameters {:path {:id String}}
    :methods {:get
              {:produces #{"application/json" "application/edn;q=0.9"}
               :response (fn [ctx]
                           (let [id (get-in ctx [:parameters :path :id])]
                             (first (filter #(= (:id %) id) items))
                             )
                           )}}
    }
   )
  )

(defn string-resource
  [x]
  (yada/as-resource x))

(defmethod ig/init-key ::string
  [_ x]
  (string-resource x))
