(ns poe-apps.store-manager.frontend.stashes
  (:require [poe-apps.store-manager.frontend.routes :as routes]
            [re-frame.core :as rf]
            [ajax.core :as ajax]
            [poe-info.item :as item]
            [poe-info.util :as util]
            [poe-info.constants :as constants]
            [clojure.string :as string]
            [cljs.pprint :as pprint]
            [poe-apps.store-manager.frontend.fragments :as fragments]))

;;;;;;;;;;;;;;;;;;;;;;; EVENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-fx
 :update-stash
 (fn [_ [_ idx]]
   {:http-xhrio
    {:method :get
     :uri (str "/api/tabs/" idx)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::update-stash-response idx]
     :on-failure [::update-stash-response-error idx]}}))

(rf/reg-event-db
 ::update-stash-response
 (fn [db [_ idx body]]
   (println "Updating stash" idx)
   (assoc-in db [:stashes idx] body)))

(rf/reg-event-fx
 ::update-stash-response-error
 (fn [_ [_ idx body]]
   (println "Error encountered loading stash" idx ":" body)
   {}))


;;;;;;;;;;;;;;;;;;;;;;; QUERIES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-sub
 ::stash
 (fn [db [_ idx]]
   (if-let [stash (-> db :stashes (get idx))]
     stash
     (do (rf/dispatch [:update-stash idx])
       nil
       )
     )
   ))

;;;;;;;;;;;;;;;;;;;;;;; VIEWS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; from poe-info/src/clj/poe-info/gems.clj


(defn lexigraphic-stash-index
  "Starts at 0 for the top left corner, and increases down and to the right.
  Good for a human understandable index (kind of). "
  [{:keys [x y]}]
  (+ y (* x constants/stash-height)))

(def type-tab-name #({"CurrencyStash" "Currency"
                      "NormalStash" "Normal"
                      "QuadStash" "Quad"
                      "MapStash" "Map"
                      "PremiumStash" "Premium"
                      "FragmentStash" "Fragment"
                      "EssenceStash" "Essence"
                      "DivinationCardStash" "Divination"} % %))

(defn stash-list-view
  []
  [:div.row>div.col
   [:div.tab-list
    (map (fn [tab]
           (let [{idx :i name :n} tab]
             [:div.tab
              {:key idx
               :style {:background-color (fragments/tab-color-style tab)}
               :title name}
              [:a {:href (routes/url-for :stashes :id idx)}
               name]]))
         @(rf/subscribe [:tab-list]))]])


(defn item-blocks-view
  [blocks]
  [:div
   {:class "item-block"}
   (interleave
    (map
     (fn [[idx1 block]] [:div {:key idx1}
                         (map
                          (fn [[idx2 line]] [:div {:key [idx1 idx2]} line])
                          (util/enumerate block))])
     (util/enumerate blocks))
    (iterate (fn [_] [:hr {:key (gensym)}]) nil))])

(defn item-table-list
  [item note?]
  [:tr {:key (:id item)}
   [:td
    (when item
      [fragments/item-link (:id item)])
    #_[:a {:href (routes/url-for :items :id (:id item))}
         (item/full-item-name item)]]

   [:td [:img {:src (:icon item)
               :title (item/item->str item)}]]

   [:td
    {:style {:background-color (fragments/item-rarity-color item)}}
    (string/capitalize (name (item/rarity item)))]

   [:td [:code (str (:category item))]]

   (when note?
     [:td
      [:pre
       (str (:note item))]])

   [:td
    [item-blocks-view (item/item->blocks item)]]])

(def stash-capacity {"NormalStash" constants/normal-stash
                     "PremiumStash" constants/normal-stash
                     "QuadStash" constants/quad-stash
                     "CurrencyStash" constants/currency-stash
                     "DivinationCardStash" constants/divination-stash
                     "EssenceStash" constants/essence-stash
                     "FragmentStash" constants/fragment-stash})

(defn stash-view
  [idx]
  (let [tab-meta @(rf/subscribe [:tab-meta idx])
        stash @(rf/subscribe [::stash idx])
        items (sort-by lexigraphic-stash-index (:items stash))
        note? (some? (first  (filter #(contains? % :note) items)))]
    [:div
     (let [{idx :i name :n stash-type :type :keys []} tab-meta
           capacity (stash-capacity stash-type)
           ;; Some stashes have single slots that hold any size
           ;; item, but count as 1 space. This leads to this number
           ;; being a little misleading.
           space (->> items (map item/size) (reduce +))]
       [:div
        [:div.row
         [:div.col-1 "Index"]
         [:div.col-1 "Name"]
         [:div.col-1 "Color"]
         [:div.col-1 "Type"]
         [:div.col-1 "Image"]
         [:div.col-1 "Items"]
         [:div.col-1 "Space"]
         [:div.col-1 "Capacity"]
         [:div.col-1 "Free"]
         [:div.col-1 "Full"]]
        [:div.row
         [:div.col-1 idx]
         [:div.col-1 (when idx [fragments/tab-link idx])]
         [:div.col-1
          [:div
           {:style  {:background-color (fragments/tab-color-style tab-meta)
                     :width "100px"
                     :height "1em"}}]]
         [:div.col-1 (type-tab-name (:type tab-meta))]
         [:div.col-1
          [:img {:src (:srcL tab-meta)}]
          [:img {:src (:srcC tab-meta)}]
          [:img {:src (:srcR tab-meta)}]]
         [:div.col-1
          [:span (count items)]]
         [:div.col-1
          [:span space]]
         [:div.col-1
          [:span capacity]]
         [:div.col-1
          [:span (- capacity space)]]
         [:div.col-1
          [:span (pprint/cl-format nil "~5F" (* 100 (/ space capacity))) "%"]]
         [:div.col-1
          [:a.button
           {:on-click #(rf/dispatch [:update-stash idx])}
           "Load"]]]])
     [:div.row>div.col
      [:table.striped
       [:thead
        [:tr
         [:th "Name"]
         [:th "Icon"]
         [:th "Rarity"]
         [:th "Category"]
         (when note?
           [:th "Note"])
         [:th "Item"]]]

       [:tbody
        (map #(item-table-list % note?) items)]]]]))
