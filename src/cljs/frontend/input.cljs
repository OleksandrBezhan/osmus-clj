(ns frontend.input)

(def canvas (.getElementById js/document "canvas"))

(defn calculate-click-angle [{:keys [click entity]}]
  (let [angle (Math/atan2 (- (:y click) (:y entity))
                          (- (:x click) (:x entity)))]
    {:angle angle :entity entity}))

(defn event-position [e]
  {:x (- (.-clientX e) (-> e .-target .getBoundingClientRect .-left))
   :y (- (.-clientY e) (-> e .-target .getBoundingClientRect .-top))})

(defn init! [{:keys [shoot-fn get-entity-fn]}]
  ;; redefine shoot on every reload, but keep single listener
  (def shoot shoot-fn)
  (defonce listen (.addEventListener
                    canvas
                    "click"
                    (fn [e]
                      (-> (calculate-click-angle {:click  (event-position e)
                                                  :entity (get-entity-fn)})
                          (shoot))))))
