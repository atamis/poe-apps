(ns poe-apps.store-manager.frontend.fragments
  (:require [re-frame.core :as rf]
            [poe-apps.store-manager.frontend.routes :as routes]
            [cljs.pprint :as pprint]
            [poe-info.item :as item]
            ))

(defn tab-color-style
  [{{:keys [r g b]} :colour}]
  (pprint/cl-format nil "rgb(~D, ~D, ~D)" r g b))

(defn tab-link
  [idx]
  (let [{name :n :as tab} @(rf/subscribe [:tab-meta idx])]
    [:span
     {:class "tab-link"}
     [:a {:href (routes/url-for :stashes :id idx)}
      name
      [:div {:class "swatch"
             :style {:background-color (tab-color-style tab)}
             }]
      ]
     ]))

(def rarity-color
  {:normal "#eeeeeeee"
   :magic "rgb(136, 120, 255)"
   :rare "rgb(245, 255, 120)"
   :unique "rgb(255, 196, 120)"
   :gem "rgb(96, 212, 206)"
   :currency "rgb(187, 187, 187)"
   :divination "rgb(255, 232, 216)"
   :prophecy "rgb(255, 120, 250)"})

(defn item-rarity-color
  [item]
  (rarity-color (item/rarity item))
  )


(defn item-link
  [id]
  (let [item @(rf/subscribe [:item-id id])]
    [:a {:href (routes/url-for :items :id id)}
     (item/full-item-name item)
     [:div {:class "swatch"
            :style {:background-color (item-rarity-color item)}}]
     ]
    )
  )
