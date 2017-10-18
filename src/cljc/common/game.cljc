(ns common.game
  #?(:clj
     (:require sc.api))
  #?(:clj
     (:import (java.util Date))))

(defn new-time []
  #?(:clj  (-> (Date.) (.getTime))
       :cljs (-> (js/Date.) (.getTime))))

(def pi #?(:cljs (-> js/Math .-PI)))

(def shot-blob-speed-multiplier 2)

(def shot-player-speed-multiplier 0.7)

(def shot-area-multiplier 0.002)

(defn compute-delta [last-time? time]
  (if last-time?
    (- time last-time?)
    0))

(defn area [{:keys [r]}]
  (* pi r r))

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

(comment
  (def entity {:x 10 :y 10 :r 5 :vx 1 :vy 1})
  (def shoot-x 1)
  (def shoot-y 0))

(defn abs [n]
  #?(:cljs (.abs js/Math n)))

(defn area-to-radius [area]
  (-> area (Math/abs) (/ pi) (Math/sqrt)))

(defn add-area [entity area]
  (let [r (area-to-radius area)
        sign (if (pos? area) 1 -1)
        r-add (* sign r)]
    (update entity :r + r-add)))

(defn create-blob [{:keys [shoot-x shoot-y entity]}]
  {:x  (-> entity :x (+ (-> entity :r (* shoot-x))))
   :y  (-> entity :y (+ (-> entity :r (* shoot-y))))
   :vx (-> entity :vx (+ (* shoot-x shot-blob-speed-multiplier)))
   :vy (-> entity :vy (+ (* shoot-y shot-blob-speed-multiplier)))
   :id 3})

(defn slice-shot-blob [{:keys [shoot-x shoot-y entity] :as args}]
  (let [area-diff (-> entity area (* shot-area-multiplier))
        blob (-> (create-blob args) (add-area area-diff))
        next-entity (add-area entity (- area-diff))]
    (println area-diff (area-to-radius area-diff))
    {:entity next-entity :blob blob}))

(defn shoot [{:keys [angle entity gen-entity-id-fn]}]
  (let [shoot-x (Math/cos angle)
        shoot-y (Math/sin angle)
        {:keys [blob entity]} (slice-shot-blob {:shoot-x shoot-x :shoot-y shoot-y :entity entity})
        next-entity (-> entity
                        (update :vx - (* shoot-x shot-player-speed-multiplier))
                        (update :vy - (* shoot-y shot-player-speed-multiplier)))]
    {:update-entity next-entity
     :add-shot-blob (assoc blob :id (gen-entity-id-fn))}))

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