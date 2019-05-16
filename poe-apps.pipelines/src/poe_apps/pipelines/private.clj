(ns poe-apps.pipelines.private
  (:require [kinsky.async :as async]
            [kinsky.client :as client]
            [clojure.core.async :as a :refer [go <! >!]]
            [clj-http.client :as http]
            [monger.core :as mg]
            [monger.collection :as mc]

            [poe-info.config :as config]
            [poe-info.api :as api]
            [poe-info.item :as item])
  (:import org.bson.types.ObjectId)
  )

(def unpriced-items-topic "unpriced-items")
(def persistence-topic "persistence")
(def price-change-topic "price-change")
(def price-difference-topic "price-diff")

(def table-name "items")


;; http client requests tabs, adds context to items, pushes
;; items to unpriced-items queue

;; service prices items from unpriced-items queue, pushes
;; them to persistence queue

;; service loads items from persistence queue into database,
;; pushes price changes to another queue


;; service reads price differential queue and alerts the user
;; to large price differentials.


(def cfg {:bootstrap.servers (config/config :kafka)})

(defn producer
  []
  (async/producer cfg :keyword :edn))

(defn print-consumer
  []
  (let [ch (async/consumer (merge cfg
                                  {:topic "test"
                                   :duplex? true
                                   :group.id "print-consumer-2"
                                   :auto.offset.reset "earliest"})
                           (client/keyword-deserializer)
                           (client/edn-deserializer))]
    (a/go-loop []
      (when-let [record (a/<! ch)]
        (println (clojure.pprint/pprint record))
        (recur)))))

(defn tab-requester
  []
  (let [username (config/config :username)
        cs (api/make-cs (config/config :poesessid))
        watched-tabs (config/config :watched-tabs)
        prod (async/producer cfg :keyword :json)]
    (loop []
      (doseq [index watched-tabs]
        (println "Loading tab " index)
        (as-> (api/stash-item-url username index) $
          (http/get $ {:cookie-store cs :as :json})
          (:body $)
          (api/stash-items-context username $)
          (map (fn [item]
                 {:topic unpriced-items-topic
                  :key (:id item)
                  :value item}) $)
          (a/onto-chan prod $ false)
          (a/<!! $)))
      (println "Done")
      (java.lang.Thread/sleep 10000)
      (recur))))

(defn item-pricer-pipeline
  [record result]
  (a/go
    (if-let [item (:value record)]
      (do (println "Pricing " (:id item))
          (api/poeprices-prediction
           (:league item)
           (item/item->str item)
           :async? true
           :respond-callback
           (fn [resp]
             (a/go
               (>! result {:topic persistence-topic
                           :key (:id item)
                           :value (assoc item
                                         :price-prediction
                                         (-> resp :body))})
               (a/close! result)))
           :raise-callback #(a/go (println %)
                                  (a/close! result))))
      (a/close! result))))

(defn item-pricer
  []
  (let [prod (async/producer cfg :keyword :json)
        [ch cntrl] (async/consumer
                    (merge cfg
                           {:topic unpriced-items-topic
                            :duplex? true
                            :group.id "item-pricer2"
                            :auto.offset.reset "earliest"})
                    :keyword :json)]

    (a/go-loop []
      (when-let [record (a/<! ch)]

        (let [result (a/chan 1)]
          (item-pricer-pipeline record result)
          (when-let [val (<! result)]
            (>! prod val))
          (<! (a/timeout 5000))))

      (recur))

    #_(a/pipeline-async
       1 ;; parallellism
       prod
       item-pricer-pipeline
       ch
       false ;; close the channel?
       )))

(defn prediction->price
  [{{:keys [min max currency]} :price-prediction}]
  (when (and min max currency)
    [:bo (/ (+ min max) 2) (item/str->currency currency)]))

(defn add-id
  [{:keys [id] :as item}]
  (when item
    (assoc item :_id id)))

(defn persister
  []
  (let [[ch cntrl] (async/consumer
                    (merge cfg
                           {:topic persistence-topic
                            :group.id (str (gensym)) #_"persister"
                            :auto.offset.reset "earliest"})
                    :keyword :json)
        prod (async/producer cfg :keyword :json)
        {:keys [conn db]} (mg/connect-via-uri (config/config :mongo-uri))]
    (a/go-loop []
      (when-let [record (a/<! ch)]
        (when-let [new-item (add-id (:value record))]
          ;; This doesn't really work
          (when-let [predicted-price (prediction->price new-item)]
            (let [list-price (item/item-price new-item)
                  price-diff (item/price-difference list-price
                                                    predicted-price)]
              (when (and price-diff (< 1 (java.lang.Math/abs price-diff)))
                (>! prod {:topic price-difference-topic
                          :key (:id new-item)
                          :value {:id (:id new-item)
                                  :predicted predicted-price
                                  :list-price list-price
                                  :difference price-diff}}))))

          (mc/update-by-id db table-name (:_id new-item)
                     new-item {:upsert true}))
        (println record)
        ;; If we get nil, the channel has closed for some reason.
        (recur)))))
