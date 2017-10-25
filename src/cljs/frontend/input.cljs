(ns frontend.input
  (:require [common.game :as game]))

(def canvas (.getElementById js/document "canvas"))


(defn event-position [e]
  {:x (- (.-clientX e) (-> e .-target .getBoundingClientRect .-left))
   :y (- (.-clientY e) (-> e .-target .getBoundingClientRect .-top))})

(defn init! [{:keys [shoot-fn get-entity-fn]}]
  (defonce listen (.addEventListener
                    canvas
                    "click"
                    (fn [e]
                      (-> (game/calculate-click-angle {:click-position (event-position e)
                                                       :entity-position         (get-entity-fn)})
                          (shoot-fn))))))
