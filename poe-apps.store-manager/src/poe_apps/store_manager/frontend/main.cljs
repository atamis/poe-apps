(ns ^:figwheel-hooks poe-apps.store-manager.frontend.main)

(js/console.log "Hello, wasdf")

;; This is called once
(defonce init
  (do (set! (.-innerHTML (js/document.getElementById "app"))
            "<p>Loaded store-manager!</p>
            <p>Edit src/poe_apps/store_manager/frontend/main.cljs to change this message.</p>")
      true))

;; This is called every time you make a code change
(defn ^:after-load reload []
  (set! (.-innerText (js/document.getElementById "app")) "Reloaded store-manager!"))
