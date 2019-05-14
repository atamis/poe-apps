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
   [manifold.deferred :as d]))

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
                                                :args [{:index index}]})
                                       first first)
                            items
                            (load-entities db
                                           (crux/q db
                                                   {:find ['item-id]
                                                    :where [['item-id :item/stash 'id]]
                                                    :args [{:id tab-id}]}))

                            tab (crux/entity db tab-id)]
                        (assoc tab :items items)))}}}))

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
                                                  [id :i _]]})
                             (load-entities db))))}}}))

(defmethod ig/init-key ::items-resource
  [_ system]
  (yada/resource
   {:parameters {:path {:id String}}
    :methods
    {:get
     {:produces #{"application/json" "application/edn;q=0.9"}
      :response (fn [ctx]
                  (let [db (crux/db system)
                        id (get-in ctx [:parameters :path :id])]
                    (->> {:find ['id]
                          :where
                          [['id :id 'item-id]]
                          :args [{:item-id id}]}
                         (crux/q db)
                         first first ;; strip crux response
                         (crux/entity db))))}}}))

(defmethod ig/init-key ::predict-resource
  [_ {:keys [predict]}]
  (yada/resource
   {:parameters {:path {:id String}}
    :methods
    {:post
     {:response
      (fn [ctx]
        (let [id (get-in ctx [:parameters :path :id])
              defer (d/deferred)]
          (clojure.core.async/>!!
           predict
           {:id id
            :callback #(d/success! defer %)}
           )
          ))
      }
     }
    }
   )
  )

(defn string-resource
  [x]
  (yada/as-resource x))

(defmethod ig/init-key ::string
  [_ x]
  (string-resource x))
