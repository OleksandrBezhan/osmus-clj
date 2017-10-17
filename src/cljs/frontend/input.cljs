(ns frontend.input
  (:require [frontend.core :as core]))

(def canvas (.getElementById js/document "canvas"))

(defn handle-click [{:keys [click entity]}]
  (let [angle (Math/atan2 (- (:y click) (:y entity))
                (- (:x click) (:x entity)))]
    angle))

(.addEventListener
  canvas
  "click"
  (fn [e]
    (handle-click {:click  {:x (- (.clientX e) (-> e .-target .getBoundingClientRect .-left))
                            :y (- (.clientY e) (-> e .-target .getBoundingClientRect .-top))}
                   :entity (-> @core/game-state :entities vals first)})))
