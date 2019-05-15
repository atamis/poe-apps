(ns poe-apps.store-manager.frontend.items
  (:require [re-frame.core :as rf]
            [cljs.pprint :as pprint]
            [poe-info.item :as item]
            [ajax.core :as ajax]

            [poe-apps.store-manager.frontend.routes :as routes]
            [poe-apps.store-manager.frontend.fragments :as fragments]

            [clojure.string :as string]))


;;;;;;;;;;;;;;;;;;;;;;; EVENTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-fx
 :update-item
 (fn [db [_ id]]
   {:http-xhrio
    {:method :get
     :uri (str "/api/items/" id)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::update-item-response id]
     :on-failure [::update-item-response-error id]}}))

(rf/reg-event-db
 ::update-item-response
 (fn [db [_ id body]]
   (println "Updating item" id)
   (assoc-in db [:items id] body)))

(rf/reg-event-fx
 ::update-item-response-error
 (fn [_ [_ idx body]]
   (println "Error encountered loading item" idx ":" body)
   {}))

(rf/reg-event-fx
 ::item-predict
 (fn [_ [_ id]]
   {:http-xhrio
    {:method :post
     :body ""
     :uri (str "/api/items/" id "/predict")
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::item-predict-response id]
     :on-failure [::item-predict-response-error id]}}))

(rf/reg-event-db
 ::item-predict-response
 (fn [db [_ id body]]
   (println "Prediction for" id " received: " body)
   (assoc-in db [:items id :item/prediction] (:item/prediction body))))

(rf/reg-event-fx
 ::item-predict-response-error
 (fn [_ [_ id body]]
   (println "Error encountered predicting " id ": " body)))


;;;;;;;;;;;;;;;;;;;;;;; QUERIES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; TODO maybe check the stash cache too.


(rf/reg-sub
 :item-id
 (fn [db [_ id]]
   (if-let [item (-> db :items (get id))]
     item
     (if-let [item (->> (:stashes db)
                        (map second)
                        (mapcat :items)
                        (filter #(= (:id %) id))
                        first)]
       item
       (do (rf/dispatch [:update-item id])
           nil)))))

;;;;;;;;;;;;;;;;;;;;;;; VIEWS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn item-view
  [id]
  (if-let [item @(rf/subscribe [:item-id id])]
    (let [sanitized-item (dissoc item :crux.db/id
                                 :item/stash :item/prediction)]
      [:div.item-view
       [:div.row>div.col
        [:h1 (item/full-item-name item)]
        [:h4.subheading id]]
       [:div.row
        [:div.col-6
         [:div [:img {:style {:float :left} :src (:icon item)}]]
         [:div "Full Name: " [fragments/item-link id]]
         (let [tab-index (item/stash-index item)]
           [:div "Stash: " [fragments/tab-link tab-index]])

         (let [{width :w height :h} item
               size (item/size item)]
           [:div "Size: " width "x" height "=" (item/size item)])

         [:div "Rarity: " (string/capitalize (name (item/rarity item)))]

         [:div "Category: " [:code (str (:category item))]]

         (when-let [sockets (:sockets item)]
           [:div "Sockets: " (item/sockets->str sockets)])

         [:div "League: " (:league item)]

         (if-let [{warning :warning_msg
                   expls :pred_explanation
                   :keys [min max currency]
                   :as prediction}
                  (:item/prediction item)]

           [:div.prediction
            [:div "Prediction: " (pprint/cl-format nil "~5F - ~5F " min max)
             currency " (" (/ (+ min max) 2) ")"]
            [:div.explanation
             (map
              (fn [[idx [mod weight]]]
                [:div {:key idx}
                 [:span.weight weight] [:span.mod mod]])
              (map vector (range) expls))]
            (when warning
              [:div.warning (string/replace warning
                                            #"prediciton"
                                            "prediciton [sic]")])]

           [:div "Prediction: None"])

         [:div
          [:a.button
           {:on-click #(rf/dispatch [::item-predict id])}
           "Predict Price"]]]

        [:div.col-6
         [:pre
          (item/item->str item)]]]

       [:div.row
        [:div.col-6
         [:pre
          (.stringify js/JSON (clj->js sanitized-item) nil 2)]]

        [:div.col-6
         [:pre
          (with-out-str
            (pprint/pprint sanitized-item))]]]
       [:div.row
        (when-let [prediction (:item/prediction item)]
          [:div.col-6 [:pre (with-out-str
                              (pprint/pprint prediction))]])]])

    [:div.row>div.col "Loading " id]))
