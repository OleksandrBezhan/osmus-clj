(ns frontend.input
  (:require [common.game :as game]))

(defn event-position [e]
  {:x (- (.-clientX e) (-> e .-target .getBoundingClientRect .-left))
   :y (- (.-clientY e) (-> e .-target .getBoundingClientRect .-top))})

(defn init! [{:keys [canvas shoot-fn get-entity-fn]}]
  (defonce listen (.addEventListener
                    canvas
                    "click"
                    (fn [e]
                      (shoot-fn (event-position e))))))
