(ns poe-apps.store-manager.predictor
  (:require [integrant.core :as ig]
            [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            ))

(defn timed-loop
  ([timeout control init tick] (timed-loop timeout control init tick (constantly nil)))
  ([timeout control init tick done]
   (a/go-loop [s init]
     (let [time-chan (a/timeout timeout)
           [_ selected] (a/alts! [control time-chan])]
       (condp = selected
         control (done s)
         time-chan (recur (tick s)))))))

(s/def ::callback fn?)

(s/def ::prediction-request
  (s/keys :req-un [::id ::callback]))


(defn prediction
  "Queries the prediction API to get prediction data, then
    updates the crux system."
  [system request]
  (let [req (s/conform ::prediction-request request)]
    (if (= req :clojure.spec.alpha/invalid)
      (log/warn "Predictor received invalid request" request)
      (let [{:keys [id callback]} req]
        (callback {:error :not-implemented
                   :id id})
        ))))

(defn predictor-main
  "Predictor main loop. Takes a crux system, a prediction channel
  and a command channel. Send prediction requests on the prediction
  channel, and close the command channel to stop the loop. Imposes
  a 5s break between requests."
  [system predict-chan command-chan]
  (a/go-loop []
    ;; read from predict or command
    ;; This is so we can stop while waiting for a prediction
    (let [[value selected] (a/alts! [predict-chan command-chan])]
      (condp = selected
        ;; done
        command-chan nil
        predict-chan
        (let [time-chan (a/timeout 5000)]
          (prediction system value)
          ;; read from timeout or command
          ;; This is so we can stop during the timeout
          (let [[value selected] (a/alts! [time-chan command-chan])]
            (condp = selected
              ;; We're ready for the next prediction
              time-chan (recur)
              ;; We're done
              command-chan nil)))))))

(defmethod ig/init-key ::predictor
  [_ system]
  (let [predict-chan (a/chan)
        command-chan (a/chan)]
    (predictor-main system predict-chan command-chan)
    {:predict predict-chan
     :command command-chan}))

(defmethod ig/halt-key! ::predictor
  [_ {:keys [command]}]
  (a/close! command))
