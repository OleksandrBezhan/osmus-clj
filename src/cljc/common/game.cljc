(ns common.game
  #?(:clj (:require sc.api))
  #?(:clj (:import (java.util Date))))

(defn new-time []
  #?(:clj  (-> (Date.) (.getTime))
     :cljs (-> (js/Date.) (.getTime))))

(def pi #?(:cljs (-> js/Math .-PI)))

(def shot-speed-multiplier 0.1)

(defn compute-delta [last-time? time]
  (if last-time?
    (- time last-time?)
    0))

(defn compute-position [{delta :delta entity :entity}]
  (let [x-delta (-> entity :vx (* (/ delta 10)))
        y-delta (-> entity :vy (* (/ delta 10)))]
    (-> entity
        (update :x + x-delta)
        (update :y + y-delta))))

(defn compute-game-state [{:keys [delta game-state] :as comp-ctx}]
  (map (fn [[entity-id entity]]
         (compute-position {:delta delta :entity entity}))
       game-state))

(defn intersects [entity1 entity2]
  false)

(defn distance-from [{:keys [small big]}]
  (let [x-distance-square (Math/pow (- (:x small) (:x big)) 2)
        y-distance-square (Math/pow (- (:y small (:y big))) 2)]
    (Math/sqrt (+ x-distance-square y-distance-square))))

(defn overlap [{:keys [small big] :as entities}]
  (-> (+ (:r small) (:r big))
      (- (distance-from entities))
      (min 0)))

(defn transfer-mass [{:keys [small big] :as entities}]
  (let [overlaption (overlap entities)
        diff (/ overlaption 2)]
    {:small (-> small (update :r - diff))
     :big   (-> big (update :r + diff))}))

(defn shoot [{:keys [angle entity]}]
  (let [ex (Math/cos angle)
        ey (Math/sin angle)
        next-entity (-> entity
                        (update :vx - (* ex shot-speed-multiplier))
                        (update :vy - (* ey shot-speed-multiplier)))]
    {:update-entity next-entity}))

(defn in-bounds [{:keys [entity game-width game-height]}]
  (and (< (:r entity) (:x entity))
       (< (:r entity) (:y entity))
       (< (:x entity) (- game-width (:r entity)))
       (< (:y entity) (- game-height (:r entity)))))

(defn reposition-in-bounds [{:keys [entity game-width game-height]}]
  (let [{:keys [x y r]} entity
        max-width (- game-width r)
        max-height (- game-height r)]
    (cond
      (< x r)
      (-> entity
          (assoc :x r)
          (update :vx -))

      (< y r)
      (-> entity
          (assoc :y r)
          (update :vy -))

      (> x max-width)
      (-> entity
          (assoc :x max-width)
          (update :vx -))

      (> y max-height)
      (-> entity
          (assoc :y max-height)
          (update :vy -))

      :else entity)))

(defn compute-entity-state [{:keys [delta entity game-width game-height] :as args}]
  (let [computed-entity (compute-position args)]
    (if (in-bounds (assoc args :entity computed-entity))
      computed-entity
      (reposition-in-bounds (assoc args :entity computed-entity)))))

(defn find-small-and-big [entity1 entity2]
  (if (< (:r entity1) (:r entity2))
    {:small entity1 :big entity2}
    {:small entity2 :big entity1}))

(defn compute-collisions [game-state]
  (for [[_ entity1] (:entities game-state)]
    (for [[_ entity2] (:entities game-state)]
      (if (and (not= entity1 entity2)
               (intersects entity1 entity2))
        (transfer-mass (find-small-and-big entity1 entity2))))))

(comment
  (deref game-state))