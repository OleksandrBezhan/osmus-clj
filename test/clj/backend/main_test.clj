(ns backend.main-test
  (:require [clojure.test :refer :all]
            [sc.api]
            [common.game :as game]
            [backend.main :refer :all]))

(defn =floating [& numbers]
  (->> numbers
       (map (fn [n] (-> (BigDecimal. n) (.setScale 5 BigDecimal/ROUND_DOWN) (.doubleValue))))
       (apply =)))

(deftest test-test
  (is (= 1 1)))

(deftest single-entity-movement
  (let [ctx {:delta 100 :game-state {:entities {1 {:id 1 :x 0 :y 0 :vx 0.1 :vy 0.1 :r 1}}
                                     :width    100 :height 100}}
        actual (game/compute-game-state ctx)
        expected (-> ctx
                     (assoc-in [:game-state :entities 1 :x] 1.0)
                     (assoc-in [:game-state :entities 1 :y] 1.0)
                     (assoc-in [:game-state :entities 1 :vx] 0.09995000000000001)
                     (assoc-in [:game-state :entities 1 :vy] 0.09995000000000001)
                     :game-state
                     )]
    (is (= actual expected) "should calculate coordinates, and slow down speed movement")))

(comment
  (sc.api/defsc 12))