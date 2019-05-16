(ns poe-info.public-pipeline
  (:require [kinsky.async :as kf]
            [kinsky.client :as kf-sync]
            #_[kinsky.client :as client]
            [clojure.core.async :as a :refer [go <! >!]]
            [aleph.http :as http]
            [clojure.data.json :as json]
            [monger.core :as mg]
            [monger.collection :as mc]
            [taoensso.timbre :as t]
            [manifold.deferred :as d]
            [byte-streams :as bs]

            [poe-info.config :as config]

            [poe-info.api :as api])
  (:import com.mongodb.WriteResult)
  )

;; Use stash_change_id to request from endpoint

;; "The property stashes.id is unique across all accounts"

(defn deferred->chan
  [deferred]
  (let [c (a/chan)
        f #(a/>!! c %)]
    (d/on-realized deferred f f)
    c))

(defn random-name [] (str (java.util.UUID/randomUUID)))


;; db['current-public-items'].createIndex({"context.id": 1})
(def stashes-topic "public-stashes")
(def items-topic "public-items")
(def all-items-table "all-public-items")
(def current-items-table "current-public-items")

(def kf-cfg {:bootstrap.servers (config/config :kafka)})

(defn dummy-requester
  []
  (let [prod (kf/producer kf-cfg :keyword :json)]
    (t/info "Starting dummy requster")
    (doseq [name ["pub-stash-1" "pub-stash-2" "pub-stash-3"]]
      (t/info "Loading " name)
      (let [resp (with-open [f (clojure.java.io/reader name)]
                   (json/read f))]
        (->> (resp "stashes")
             (map (fn [stash] {:topic stashes-topic :value stash}))
             (a/onto-chan prod)
             (a/<!!))))

    (t/info "Finished dummy requester")))

(defn stash-requester
  [id]
  (a/<!! (a/go
           (t/info "Starting stash requester with id" id)
           (let [prod (kf-sync/producer kf-cfg :keyword :json)]
             (<!
              (a/go-loop [id id]
                (t/info "Loading with id" id)
                (let [json (-> (http/get (api/public-stash-tabs-url id))
                               deferred->chan
                               <!
                               :body
                               bs/to-string
                               json/read-str)
                      ]

                  (t/info "Downloaded " (count (json "stashes")) "stashes")

                  (doseq [stash (json "stashes")]
                    (kf-sync/send! prod stashes-topic (stash "id") stash)
                    )

                  #_(->> (json "stashes")
                       (map (fn [stash] {:topic stashes-topic :value stash}))
                       (a/onto-chan prod)
                       (a/<!))

                  (t/info "Finished loading with id" id)

                  (if (= id (json "next_change_id"))
                    (do
                      (t/info "Identical ids, sleeping for 20s")
                      (<! (a/timeout 20000)))
                    (do
                      (t/info "Different ids, throttling 1s")
                      (<! (a/timeout 1000))))
                  (recur (json "next_change_id"))))))

           (t/info "Stopping stash requester"))))

(defn- stash-add-context
  [stash]
  (let [context (select-keys stash [:id :public :accountName :lastCharacterName :stash :stashType :league])]
    (update stash :items (fn [items] (map #(assoc % :context context) items)))))

(defn stash-processor
  [record done]
  (a/go
    (if-let [stash (:value record)]
      (do
        (->> (stash-add-context stash)
             (map (fn [item] {:topic items-topic :value item}))
             (a/onto-chan done) ;; autocloses
             (<!)))

      (do (println record)
          (a/close! done)))))

(defn stash-processors
  [n]
  (let [[in-ch cntrl] (kf/consumer
                       (merge kf-cfg
                              {:topic stashes-topic
                               :group.id "stash-processors"
                               :auto.offset.reset "earliest"})
                       :keyword :json)
        prod (kf/producer kf-cfg :keyword :json)]

    (t/info "Starting stash-processor pipeline")
    (a/pipeline-async
     n
     prod
     stash-processor
     in-ch
     false)
    (t/info "Finished stash-processor")))

(defn timed-loop
  ([timeout control init tick] (timed-loop timeout control init tick (constantly nil)))
  ([timeout control init tick done]
   (a/go-loop [s init]
     (let [time-chan (a/timeout timeout)
           [_ selected] (a/alts! [control time-chan])]
       (condp = selected
         control (done s)
         time-chan (recur (tick s)))))))

(defn add-id
  [{:keys [id] :as item}]
  (when item
    (assoc item :_id id)))

(defn persister
  []
  (let [[ch cntrl] (kf/consumer
                    (merge kf-cfg
                           {:topic items-topic
                            :group.id "persister"
                            :auto.offset.reset "earliest"})
                    :keyword :json)
        {:keys [conn db]} (mg/connect-via-uri (config/config :mongo-uri))
        control (a/chan)]
    (a/go

      (t/info "Starting persister")

      (t/info "Starting persister tracker")
      (timed-loop 60000 control nil
                  (fn [_] (t/info "Items persisted: " (mc/count db all-items-table)))
                  (fn [_] (t/info "Persister tracker done")))

      (<! (a/go-loop []
            (when-let [record (a/<! ch)]
              (when-let [new-item (:value record)]
                (when-let [id (:id new-item)]
                  (mc/update-by-id db all-items-table (:id new-item)
                                   new-item {:upsert true})))
              ;; If we get nil, the channel has closed for some reason.
              (recur))))
      (t/info "Finished persister"))))

(defmulti result->map class)

(defmethod result->map WriteResult
  [^WriteResult res]
  {:write (.getN res)}
  )

(defn update-stash-items
  [db coll new-stash]
  (let [new-stash (stash-add-context new-stash)
        {stash-id :id} new-stash
        existing-items (->> (mc/find-maps db coll
                                          {:context.id {:$eq stash-id}}
                                          {:id 1} true)
                            (map :id)
                            (into #{}))
        new-items (->> new-stash :items
                       (map :id)
                       (into #{}))
        ;; If an item was removed from the tab, it won't be present in new-items
        ;; so we can find those items that are no longer in the stash, and
        ;; then remove them.
        removed-items (clojure.set/difference existing-items new-items)]
    ;; Ideally, we'd begin a transaction here.

    #_(concat (map #(mc/remove-by-id db coll %) removed-items)
            (map #(mc/update-by-id db coll (:id %)
                                   % {:upsert true}) (:items new-stash))
            )

    #_(doseq [id removed-items]
      (mc/remove-by-id db coll id))

    #_(doseq [item (:items new-stash)]
      (mc/update-by-id db coll (:id item)
                       item {:upsert true}))

    (let [write-result (->> (concat (map #(mc/remove-by-id db coll %) removed-items)
                                    (map #(mc/update-by-id db coll (:id %)
                                                           % {:upsert true}) (:items new-stash))
                                    )
                            (map result->map)
                            (apply merge-with +)
                            )
          removed (count removed-items)
          inserted (count (:items new-stash))
          ;; count actually removed too
          ]
      (when (some #(not= % 0) [removed inserted])
        (t/info "Removed" (count removed-items) "items,"
                "inserted/updated" (count (:items new-stash)) "items, confirmed"
                (:write write-result))
        )
      )

    ;; Ideally, we'd end the transaction here.
    ))

(defn current-items-persister
  []
  (go
    (t/info "Starting current-item-persister")
    (let [[ch cntrl] (kf/consumer
                      (merge kf-cfg
                                  {:topic stashes-topic
                                   :group.id "current-item-persister"
                                   ;;:auto.offset.reset "earliest"
                                   })
                      :keyword :json)
          {:keys [conn db]} (mg/connect-via-uri (config/config :mongo-uri))
          ]

      (<!
       (a/go-loop []
         (when-let [record (<! ch)]
           (update-stash-items db current-items-table (:value record))
           (recur)
           )
         )
       )
      (t/info "Finishing current-item-persister")
       )
    ))

(defn current-persisters
  [n]
  (t/info "Starting" n "current-persisters")
  (->> n
       range
       (map (fn [_] (current-items-persister)))
       (map a/<!!)
       dorun
       )
  
  (t/info "Finishing" n "current-persisters")
  )

(comment

  (def db (:db (mg/connect-via-uri (config/config :mongo-uri)))))

