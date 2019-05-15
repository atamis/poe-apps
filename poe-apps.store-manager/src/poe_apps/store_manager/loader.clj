(ns poe-apps.store-manager.loader
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [poe-info.item :as item]
            [crux.api :as crux]
            [poe-apps.store-manager.data :as data]
            ))

(comment
  (def system (get integrant.repl.state/system
                   [:juxt.crux.ig.system/cluster-node
                    :poe-apps.store-manager/system]))

  (def db (crux/db system))

  (def tx (->> (cached-files)
               (map vector (range))
               (map (fn [[id stash]] (load-stash-tx id stash)))
               (reduce into)))

  (def crux-system (get integrant.repl.state/system [:juxt.crux.ig.system/cluster-node :poe-apps.store-manager/system]))

  (count
   (crux/q (crux/db system) '{:find [id]
                              :where [[_ :crux.db/id id]]}))

  (crux/sync system (java.time.duration/ofminutes 10)))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn cached-files
  []
  (->> (io/resource "cache/")
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

(defn item->tx
  [stash-id item]
  (let [item-uuid (data/entity-id item)]
    [:crux.tx/put item-uuid
     (merge item {:crux.db/id item-uuid
                  :item/stash stash-id})]))

(defn load-stash-tx
  [stash-index {:keys [tabs items] :as stash}]
  (let [tab-info (nth tabs stash-index)
        tab-uuid (data/entity-id tab-info)]
    (into [[:crux.tx/put tab-uuid
            (assoc tab-info :crux.db/id tab-uuid)]]
          (map (partial item->tx tab-uuid) items))))

(defn purge-system
  [system]
  (->> (crux/q (crux/db system) '{:find [id]
                                  :where [[_ :crux.db/id id]]})
       (map first)
       (map (fn [id] [:crux.tx/evict id]))
       (into [])
       (crux/submit-tx system)))

#_"035c37b7b1a20c8d92644361fc88ed5a3e77cce4c6cfb8ea82d9a627da06977c"
