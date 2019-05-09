(ns poe-apps.store-manager.tabs
  (:require
   [yada.yada :as yada]
   [integrant.core :as ig]
   [clojure.data.json :as json]

   [clojure.java.io :as io]
   [clojure.string :as string]
   [crux.api :as crux]
   [crux.codec :as codec]
   [cheshire.generate :as generate]
   ))

(defn load-entities
  [db seq]
  (map
   (fn [id?]
     (let [id (if (vector? id?) (first id?) id?)]
       (crux/entity db id)))
   seq))

(generate/add-encoder crux.codec.Id
                      (fn [c jsonGenerator]
                        (.writeString jsonGenerator
                                      (str c))))

#_
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
(def files [])

(defmethod ig/init-key ::cached
  [_ system]
  (yada/resource
   {:parameters {:path {:id Long}}
    :methods {:get {:produces #{"application/json"
                                "application/edn;q=0.9"}
                    :response
                    (fn [ctx]
                      (let [db (crux/db system)
                            index (get-in ctx [:parameters :path :id])
                            tab-id (-> (crux/q db
                                               {:find ['id]
                                                :where [['id :i 'index]]
                                                :args [{:index index}]
                                                })
                                       first first
                                       )
                            tab (crux/entity db tab-id)
                            ]
                        tab
                        #_(nth files (get-in ctx [:parameters :path :id]))))}}}))

(def tabs (->> files first :tabs))

(defmethod ig/init-key ::tabs-resource
  [_ system]
  (yada/resource
   {:methods {:get {:produces #{"application/json"
                                "application/edn;q=0.9"}
                    :response
                    (fn [ctx]
                      (let [db (crux/db system)]
                        (->> (crux/q db '{:find [id]
                                          :where [[id :n _]
                                                  [id :i _]]
                                          })
                             (load-entities db)
                             )
                        )
                      )}
              }}
   ))

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
