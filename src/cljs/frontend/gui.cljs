(ns frontend.gui
  (:require [frontend.input :as input]
            [common.game :as game]
            [cljs.pprint :as pprint]))

;; constants
(def my-entity-color "#1E90FF")
(def small-enemy-color "#008000")
(def medium-enemy-color "#FFA500")
(def big-enemy-color "#DC143C")

(defn format-number
  "Format a float number"
  [n]
  (pprint/cl-format nil "~,1f" n))

(defn render-entity-debug [{:keys [entity x y]}]
  (let [ex (:x entity)
        ey (:y entity)
        evx (:vx entity)
        evy (:vy entity)
        er (:r entity)
        texts [(str "  xy: [" (format-number ex) " " (format-number ey) "]")
               (str "vxy: [" (format-number evx) " " (format-number evy) "]")
               (str "    r: [" (format-number er) "]")]]
    [{:fill-style "Black"}
     {:font "normal 10pt Arial"}
     (map-indexed (fn [i text]
                    {:fill-text {:text text
                                 :x    x
                                 :y    (+ y (* i 20))}})
                  texts)]))

(defn enemy-color [enemy-r my-r]
  (cond
    (-> enemy-r (< (* 0.8 my-r))) small-enemy-color
    (-> enemy-r (< my-r)) medium-enemy-color
    (-> enemy-r (= my-r)) big-enemy-color
    (-> enemy-r (> my-r)) big-enemy-color))

(defn render-entity [entity is-rendering-debug my-entity]
  (let [is-my-entity (-> (:id entity) (= (:id my-entity)))]
    [{:fill-style (if is-my-entity my-entity-color (enemy-color (:r entity) (:r my-entity)))}
     {:begin-path true}
     {:arc {
            :x                (:x entity)
            :y                (:y entity)
            :r                (:r entity)
            :start-angle      0
            :end-angle        (* 2 game/pi)
            :is-anticlockwise true}}
     {:close-path true}
     {:fill true}
     (if is-rendering-debug
       (render-entity-debug {:entity entity
                             :x      (- (:x entity) (/ (:r entity) 2))
                             :y      (+ (:y entity) (:r entity) 20)}))]))

(defn render-entities [{:keys [width height entities is-rendering-debug my-entity]}]
  (let [my-entity ()])
  [{:clear-rect {:x1 0 :y1 0 :x2 width :y2 height}}
   (for [entity entities]
     (render-entity entity is-rendering-debug my-entity))])

(defn render-fps [delta]
  (when (> delta 0)
    (let [fps (-> (/ 1 delta) (* 1000))]
      [{:fill-style "Black"}
       {:font "normal 16pt Arial"}
       {:fill-text {:text (str (int fps) " fps")
                    :x    10
                    :y    26}}])))

(defn request-animation-frame! [f]
  (.requestAnimationFrame js/window f))

(defn clear-rect! [c-ctx {:keys [x1 y1 x2 y2]}]
  (.clearRect c-ctx x1 y1 x2 y2))

(defn fill-style! [c-ctx color]
  (aset c-ctx "fillStyle" color))

(defn fill-text! [c-ctx {:keys [text x y] :as args}]
  (.fillText c-ctx text x y))

(defn begin-path! [c-ctx]
  (.beginPath c-ctx))

(defn close-path! [c-ctx]
  (.closePath c-ctx))

(defn arc! [c-ctx {:keys [x y r start-angle end-angle is-anticlockwise]}]
  (.arc c-ctx x y r start-angle end-angle is-anticlockwise))

(defn fill! [c-ctx]
  (.fill c-ctx))

(defn set-last-render-time! [time render-state]
  (swap! render-state assoc :last-render-time time))

(defn set-last-frame-time! [time render-state]
  (swap! render-state assoc :last-frame-time time))

(defn font! [c-ctx font]
  (aset c-ctx "font" font))

(defn add-entity! [entity game-state]
  (swap! game-state update :entities assoc (:id entity) entity))

(defn add-shot-blob! [blob game-state]
  (add-entity! blob game-state))

(defn gen-entity-id! [game-state]
  (let [id (->> @game-state :entities keys (apply max) (+ 1))
        entity {:id id}]
    (add-entity! entity game-state)
    id))

(defn update-entity! [entity game-state]
  (swap! game-state update :entities assoc (:id entity) entity))

(defn set-game-state! [next-game-state game-state]
  (reset! game-state next-game-state))

(defn mutator!
  [mutation game-state render-state]
  (if (map? mutation)
    (do
      (let [{:keys [clear-rect
                    begin-path
                    close-path
                    arc
                    fill
                    fill-text
                    fill-style
                    font
                    set-last-render-time
                    set-last-frame-time
                    update-entity
                    update-fps
                    add-shot-blob
                    set-game-state]} mutation
            c-ctx (:c-ctx @render-state)]
        (when clear-rect (clear-rect! c-ctx clear-rect))
        (when font (font! c-ctx font))
        (when fill-text (fill-text! c-ctx fill-text))
        (when fill-style (fill-style! c-ctx fill-style))
        (when begin-path (begin-path! c-ctx))
        (when close-path (close-path! c-ctx))
        (when arc (arc! c-ctx arc))
        (when fill (fill! c-ctx))
        (when set-last-render-time (set-last-render-time! set-last-render-time render-state))
        (when set-last-frame-time (set-last-frame-time! set-last-frame-time render-state))
        (when add-shot-blob (add-shot-blob! add-shot-blob game-state))
        (when set-game-state (set-game-state! set-game-state game-state))
        (when update-entity (update-entity! update-entity game-state))))

    (doseq [mut mutation]
      (mutator! mut game-state render-state))))

(defn shoot! [shoot-pos game-state render-state]
  (let [entity (-> @game-state :entities (get (:entity-id @game-state)))]
    (-> (game/shoot {:shoot-pos   shoot-pos
                     :gen-entity-id-fn (fn [] (gen-entity-id! game-state))
                     :entity           entity})
        (mutator! game-state render-state))))

(defn render-frame [time game-state render-state]
  (let [is-frame-throttling (:is-frame-throttling @render-state)
        frame-throttling-threshold (:frame-throttling-threshold @render-state)
        is-rendering-debug (:is-rendering-debug @render-state)
        frame-throttling-delta (- time (:last-render-time @render-state))]

    (if (or (not is-frame-throttling) (and is-frame-throttling (> frame-throttling-delta frame-throttling-threshold)))
      (let [delta (game/compute-delta (:last-frame-time @render-state) time)
            next-game-state (game/compute-game-state {:delta delta :game-state @game-state})]
        (-> (render-entities {:width              (:width next-game-state)
                              :height             (:height next-game-state)
                              :entities           (vals (:entities next-game-state))
                              :delta              delta
                              :is-rendering-debug is-rendering-debug
                              :my-entity          (-> next-game-state :entities (get (:entity-id next-game-state)))})
            (conj (when is-rendering-debug (render-fps delta)))
            (conj {:set-last-render-time time
                   :set-last-frame-time  time
                   :set-game-state       next-game-state})))

      {:set-last-frame-time time})))

(defn render-frame-loop! [game-state render-state]
  (letfn [(render-frame! [time]
            (-> time
                (render-frame game-state render-state)
                (mutator! game-state render-state)
                ((fn [_] (request-animation-frame! render-frame!)))))]

    (request-animation-frame! render-frame!)))

(defn start! [game-state render-state]
  (render-frame-loop! game-state render-state)
  (input/init! (:canvas @render-state)
               (fn shoot-fn [shoot-pos] (shoot! shoot-pos game-state render-state))))

(defn mk-initial-game-state [canvas]
  (atom {:entities  {1 {:id 1
                        :x  80
                        :y  150
                        :vx 0
                        :vy 0
                        :r  50}}
         :width     (.-width canvas)
         :height    (.-height canvas)
         :entity-id 1}))

(defn mk-initial-render-state [canvas c-ctx]
  (atom {:last-render-time           nil
         :last-frame-time            nil
         :is-rendering-debug         false
         :is-frame-throttling        false
         :frame-throttling-threshold 10000
         :canvas                     canvas
         :c-ctx                      c-ctx}))

(defn main! []
  (let [canvas (.getElementById js/document "canvas")
        c-ctx (.getContext canvas "2d")
        game-state (mk-initial-game-state canvas)
        render-state (mk-initial-render-state canvas c-ctx)]

    (def dev-game-state game-state)
    (def dev-render-state render-state)

    (enable-console-print!)
    (start! game-state render-state)))