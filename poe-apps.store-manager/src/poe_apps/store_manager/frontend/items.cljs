(ns poe-apps.store-manager.frontend.items
  (:require [re-frame.core :as rf]
            [cljs.pprint :as pprint]
            [poe-info.item :as item]

            [poe-apps.store-manager.frontend.routes :as routes]))

;;;;;;;;;;;;;;;;;;;;;;; QUERIES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :item-id
 (fn [db [_ id]]
   (->> (:stashes db)
        (map second)
        (mapcat :items)
        (filter #(= (:id %) id))
        first)))

;;;;;;;;;;;;;;;;;;;;;;; VIEWS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn item-view
  [id]
  (if-let [item @(rf/subscribe [:item-id id])]
    [:div.item-view
     [:div.row>div.col
      [:h1 (item/full-item-name item)]
      [:h4.subheading id]]
     [:div.row
      [:div.col-6
       [:div [:img {:src (:icon item)}]]
       (let [tab-index (item/stash-index item)
             tab-meta @(rf/subscribe [:tab-meta tab-index])]
         [:div "Stash: " [:a {:href (routes/url-for :stashes :id tab-index)}
                          (:n tab-meta)]])]

      [:div.col-6
       [:pre
        (item/item->str item)]]]

     [:div.row
      [:div.col-6
       [:pre
        (.stringify js/JSON (clj->js item) nil 2)]]

      [:div.col-6
       [:pre
        (with-out-str
          (pprint/pprint item))]]]]

    [:div.row>div.col "Loading " id]))
