(ns frontend.input)

(defn event-position [e]
  {:x (- (.-clientX e) (-> e .-target .getBoundingClientRect .-left))
   :y (- (.-clientY e) (-> e .-target .getBoundingClientRect .-top))})

(defn init! [canvas shoot-fn]
  (defonce listen (.addEventListener
                    canvas
                    "click"
                    (fn [e] (shoot-fn (event-position e))))))
