(ns ^:figwheel-hooks poe-apps.store-manager.frontend.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [poe-apps.store-manager.frontend.routes :as routes]
            [poe-apps.store-manager.frontend.stashes :as stashes]
            [poe-apps.store-manager.frontend.items :as items-frontend]
            [poe-apps.store-manager.frontend.fragments :as fragments]
            [cljs.pprint :as pprint]
            ))


;;;;;;;;;;;;;;;;;;;;;;; EVENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-db
 :active-route
 (fn [db [_ route]]
   (assoc db :active-route route)))

(rf/reg-event-db
 :initialize
 (fn [_ _]
   (rf/dispatch [:update-tab-list nil])
   {:tab-list []
    :active-route {:handler :home}
    :stashes {}
    }))

(rf/reg-event-fx
 :update-tab-list
 (fn [_ _]
   {:http-xhrio
    {:method :get
     :uri "/api/tabs"
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [:tab-update-response]
     :on-failure [:tab-update-response-error]}}))

(rf/reg-event-db
 :tab-update-response
 (fn [db [_ body]]
   #_
   (doseq [idx (map :i body)]
     (rf/dispatch [:update-stash idx])
     )
   (assoc db :tab-list body)))

(rf/reg-event-db
 :tab-update-response-error
 (fn [db [_ body]]
   (println body)
   db))


;;;;;;;;;;;;;;;;;;;;;;; QUERIES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :tab-list
 (fn [db _]
   (:tab-list db)))

(rf/reg-sub
 :active-route
 (fn [db _]
   (:active-route db)))

(rf/reg-sub
 :stash-list
 (fn [db _]
   (-> db :stashes keys sort)))

(rf/reg-sub
 :tab-meta
 (fn [db [_ idx]]
   (-> db :tab-list (get idx))
   )
 )

;;;;;;;;;;;;;;;;;;;;;;; VIEWS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tab-list-row
  [tab]
  [:tr
   {:key (:i tab)}
   [:td (:i tab)]
   [:td [fragments/tab-link (:i tab)]]
   [:td
    [:div
     {:style  {:background-color (fragments/tab-color-style tab)
               :width "100px"
               :height "1em"}}]]
   [:td (stashes/type-tab-name (:type tab))]
   [:td
    [:img {:src (:srcL tab)}]
    [:img {:src (:srcC tab)}]
    [:img {:src (:srcR tab)}]]
   [:td
    [:a.button
     {:on-click #(rf/dispatch [:update-stash (:i tab)])}
     "Load"]
    ]
   ])

(defn tab-list-view
  []
  (let [tab-list @(rf/subscribe [:tab-list])]
    [:div.row>div.col "Tabs: " (count tab-list)]
    [:div.row>div.col
     [:div.tab-list
      [:table.striped
       [:thead>tr
        [:th "Index"]
        [:th "Name"]
        [:th "Color"]
        [:th "Type"]
        [:th "Image"]
        [:th ""]
        ]
       [:tbody
        (map tab-list-row tab-list)]]]]))

(defmulti page :handler)

(defmethod page :home
  [_]
  [:div.row>div.col "Home"])

(defmethod page :default
  [_]
  [:div.row>div.col "Not found"])

(defmethod page :tabs
  [_]
  [tab-list-view])

(defmethod page :stashes-home
  [_]
  [stashes/stash-list-view])

(defmethod page :stashes
  [_]
  [:div
   [stashes/stash-list-view]
   (let [idx (-> (rf/subscribe [:active-route])
                 deref
                 :route-params
                 :id
                 js/parseInt)]
     [stashes/stash-view idx]
     )]
  )

(defmethod page :items
  [_]
  (let [id (-> (rf/subscribe [:active-route])
               deref
               :route-params
               :id)]
    [items-frontend/item-view id]
    )
  )

(defmethod page :about
  [_]
  [:div.row>div.col "Nice try"])

(defn ui
  []
  [:div
   [:div.row
    [:div.col-1>a {:href (routes/url-for :home)} "Home"]
    [:div.col-1>a {:href (routes/url-for :tabs)} "Tabs"]
    [:div.col-1>a {:href (routes/url-for :stashes-home)} "Stashes"]
    [:div.col-1>a {:href (routes/url-for :about)} "About"]]
   [page @(rf/subscribe [:active-route])]])

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (routes/app-routes)
  (reagent/render [ui]
                  (js/document.getElementById "app")))

;; This is called once
(defonce init
  (do (run)
      true))


;; This is called every time you make a code change


(defn ^:after-load reload []
  (rf/clear-subscription-cache!)
  (reagent/render [ui]
                  (js/document.getElementById "app"))
    #_(set! (.-innerText (js/document.getElementById "app")) "Reloaded store-manager!"))
