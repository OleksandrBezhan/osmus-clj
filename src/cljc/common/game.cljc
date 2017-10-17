(ns common.game
  #?(:clj (:require sc.api))
  #?(:clj (:import (java.util Date))))

(defn new-time []
  #?(:clj  (-> (Date.) (.getTime))
     :cljs (-> (js/Date.) (.getTime))))

(def state-time (atom (new-time)))

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

(def game-state (atom nil))

(defn load-game-state [new-game-state]
  (reset! game-state new-game-state))

(load-game-state {:entities {1 {
                                :type :blob
                                :id   1
                                :x    100
                                :y    400
                                :r    10
                                :vx   0.1
                                :vy   -0.1
                                }
                             2 {
                                :type :player
                                :id   1
                                :name "Alex"
                                :x    200
                                :y    100
                                :r    5
                                :vx   -0.1
                                :vy   0.2
                                }}})

(comment
  ;(def delta (- (new-time) @state-time))
  (deref game-state)
  (compute-game-state {:delta 1000 :game-state @game-state})
  )