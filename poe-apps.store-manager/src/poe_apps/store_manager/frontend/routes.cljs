(ns poe-apps.store-manager.frontend.routes
  (:require [re-frame.core :as rf]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [clojure.string :as string]
   )
  )

(def routes ["/" {"" :home
                  "tabs" :tabs
                  "stashes/" {"" :stashes-home
                              ":id/" :stashes-index
                              }
                  "about" :about}])


(defn parse-url
  [url]
  (bidi/match-route routes (string/join "" (rest (string/split url #"#"))))
  )

(defn dispatch-route
  [matched-route]
  (rf/dispatch [:active-route matched-route]))

(defn app-routes []
  (pushy/start! (pushy/pushy dispatch-route parse-url)))

(def url-for #(str "/#" (bidi/path-for routes %)))
