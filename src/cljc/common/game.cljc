(ns common.game
  (:require [clojure.math.combinatorics :as combo])
  #?(:clj
     (:require [sc.api :as sc]))
  #?(:clj
     (:import (java.util Date))))

(defn new-time []
  #?(:clj  (-> (Date.) (.getTime))
     :cljs (-> (js/Date.) (.getTime))))

(def pi #?(:cljs (-> js/Math .-PI)
           :clj (Math/PI)))

(def shot-blob-speed-multiplier 2)

(def shot-player-speed-multiplier 0.7)

(def shot-area-multiplier 0.002)

(def area-transfer-rate 0.05)

(def min-alive-radius 1)

(defn compute-delta [last-time? time]
  (if last-time?
    (- time last-time?)
    0))

(defn area [{:keys [r]}]
  (* pi r r))

(defn abs [n]
  #?(:cljs (.abs js/Math n)))

(defn area-to-radius [area]
  (-> area (Math/abs) (/ pi) (Math/sqrt)))

(defn add-area [entity area]
  (let [r (area-to-radius area)
        sign (if (pos? area) 1 -1)
        r-add (* sign r)]
    (update entity :r + r-add)))

(defn create-blob [{:keys [shoot-x shoot-y entity gen-entity-id-fn]}]
  {:x  (-> entity :x (+ (-> entity :r (* shoot-x))))
   :y  (-> entity :y (+ (-> entity :r (* shoot-y))))
   :vx (-> entity :vx (+ (* shoot-x shot-blob-speed-multiplier)))
   :vy (-> entity :vy (+ (* shoot-y shot-blob-speed-multiplier)))
   :r 0
   :id (gen-entity-id-fn)})

(defn slice-shot-blob [{:keys [shoot-x shoot-y entity gen-entity-id-fn] :as args}]
  (let [area-diff (-> entity area (* shot-area-multiplier))
        blob (-> (create-blob args) (add-area area-diff))
        next-entity (add-area entity (- area-diff))]
    {:entity next-entity :blob blob}))

(defn shoot [{:keys [angle entity gen-entity-id-fn] :as args}]
  (let [shoot-x (Math/cos angle)
        shoot-y (Math/sin angle)
        {:keys [blob entity]} (slice-shot-blob {:shoot-x shoot-x :shoot-y shoot-y :entity entity :gen-entity-id-fn gen-entity-id-fn})
        next-entity (-> entity
                        (update :vx - (* shoot-x shot-player-speed-multiplier))
                        (update :vy - (* shoot-y shot-player-speed-multiplier)))]
    {:update-entity next-entity
     :add-shot-blob blob}))

(comment
  (sc.api/defsc 21)
  (shoot {:angle            -0.0046050780950956,
          :entity           {:id 1, :x 155.69990326972183, :y 200, :vx 0, :vy 0, :r 155.69990326972183},
          :gen-entity-id-fn (constantly 3)}))

(defn in-bounds [{:keys [entity width height] :as ctx}]
  (and (< (:r entity) (:x entity))
       (< (:x entity) (- width (:r entity)))
       (< (:r entity) (:y entity))
       (< (:y entity) (- height (:r entity)))))

(defn reposition-in-bounds [{:keys [entity width height]}]
  (let [{:keys [x y r]} entity
        max-width (- width r)
        max-height (- height r)]
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

(defn small-big [entity1 entity2]
  (if (< (:r entity1) (:r entity2))
    {:small entity1 :big entity2}
    {:small entity2 :big entity1}))

(defn distance-from [entity1 entity2]
  (let [x-distance-square (-> (- (:x entity1) (:x entity2)) (Math/pow 2))
        y-distance-square (-> (- (:y entity1) (:y entity2)) (Math/pow 2))]
    (Math/sqrt (+ x-distance-square y-distance-square))))

(defn overlap [entity1 entity2]
  (let [over (-> (+ (:r entity1) (:r entity2))
                 (- (distance-from entity1 entity2)))]
    (if (pos? over) over 0)))

(defn transfer-areas [entity1 entity2]
  (let [overlap-value (overlap entity1 entity2)
        {:keys [small big] :as sb} (small-big entity1 entity2)
        diff (* overlap-value area-transfer-rate)
        result {:small (-> small (update :r - diff))
                :big   (-> big (update :r + diff))}]
    result))

(defn intersects [entity1 entity2]
  (-> (distance-from entity1 entity2)
      (< (+ (:r entity1) (:r entity2)))))

(defn compute-entity-position [{delta :delta entity :entity}]
  (let [x-delta (-> entity :vx (* (/ delta 10)))
        y-delta (-> entity :vy (* (/ delta 10)))]
    (-> entity
        (update :x + x-delta)
        (update :y + y-delta))))

(defn move-entity [{:keys [entity delta game-state] :as ctx}]
  (let [computed-entity (compute-entity-position ctx)]
    (if (in-bounds (-> ctx (assoc :entity computed-entity :width (:width game-state) :height (:height game-state))))
      computed-entity
      (reposition-in-bounds (-> ctx (assoc :entity computed-entity :width (:width game-state) :height (:height game-state)))))))

(defn move-entities [{:keys [delta game-state] :as ctx}]
  (reduce-kv
    (fn [m id entity]
      (assoc m id (move-entity (-> ctx (assoc :entity entity)))))
    {}
    (:entities game-state)))

(defn compute-collisions [game-state]
  (let [entities-atom (atom (:entities game-state))]
    (doseq [[entity1 entity2] (combo/combinations (vals @entities-atom) 2)]
      (when (intersects entity1 entity2)
        (let [{:keys [small big] :as sb} (transfer-areas entity1 entity2)]
          (if (-> (:r small) (< min-alive-radius))
            (-> entities-atom
                (swap! (fn [entities-value]
                         (-> entities-value
                             (assoc (:id big) big)
                             (dissoc (:id small))))))
            (-> entities-atom
                (swap! assoc
                       (:id small) small
                       (:id big) big))))))

    (assoc game-state :entities @entities-atom)))

(defn compute-game-state [{:keys [delta game-state] :as ctx}]
  (let [next-entities (move-entities ctx)]
    (-> game-state
        (assoc :entities next-entities)
        (compute-collisions))))