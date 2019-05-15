(ns poe-apps.store-manager.data
  (:require [crux.api :as crux])
  )


(defn entity-id
  [{:keys [id]}]
  (crux.codec/new-id id))

(defn entity-id-lookup
  [db id]
  (-> (crux/q db {:find ['e-id]
                  :where [['e-id :id 'id]]
                  :args [{:id id}]
                  })
      first first)
  )
