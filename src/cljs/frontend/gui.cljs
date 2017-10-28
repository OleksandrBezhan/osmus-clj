(ns frontend.gui
  (:require [common.hello :refer [foo-cljc]]
            [frontend.input :as input]
            [common.game :as game]
            [foo.bar]
            [cljs.pprint :as pprint]))

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
                                 :y    (+ y (* i 20))}}) texts)]))

(defn render-entity [entity is-rendering-debug]
  [{:fill-style "green"}
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
                           :y      (+ (:y entity) (:r entity) 20)}))])

(defn render-entities [{:keys [delta width height entities is-rendering-debug]}]
  [{:clear-rect {:x1 0 :y1 0 :x2 width :y2 height}}
   (for [entity entities]
     (render-entity entity is-rendering-debug))])

(defn render-fps [delta]
  (when (> delta 0)
    (let [fps (-> (/ 1 delta) (* 1000))]
      [{:fill-style "Black"}
       {:font "normal 16pt Arial"}
       {:fill-text {:text (str (int fps) " fps")
                    :x    10
                    :y    26}}])))

(defn request-animation-frame! [handler]
  (.requestAnimationFrame js/window handler))

(defn clear-rect! [c-context {:keys [x1 y1 x2 y2]}]
  (.clearRect c-context x1 y1 x2 y2))

(defn fill-style! [c-context color]
  (aset c-context "fillStyle" color))

(defn fill-text! [c-context {:keys [text x y] :as args}]
  (.fillText c-context text x y))

(defn begin-path! [c-context]
  (.beginPath c-context))

(defn close-path! [c-context]
  (.closePath c-context))

(defn arc! [c-context {:keys [x y r start-angle end-angle is-anticlockwise]}]
  (.arc c-context x y r start-angle end-angle is-anticlockwise))

(defn fill! [c-context]
  (.fill c-context))

(defn set-last-render-time! [time render-state]
  (swap! render-state assoc :last-render-time time))

(defn set-last-frame-time! [time render-state]
  (swap! render-state assoc :last-frame-time time))

(defn font! [c-context font]
  (aset c-context "font" font))

(defn add-entity! [entity game-state]
  (swap! game-state update :entities assoc (:id entity) entity))

(defn add-shot-blob! [blob game-state]
  (add-entity! blob game-state))

(defn gen-entity-id! [game-state]
  (let [id (->> @game-state :entities keys (apply max) (+ 1))]
    (add-entity! {:id id} game-state)
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
            c-context (:c-context @render-state)]
        (when clear-rect (clear-rect! c-context clear-rect))
        (when font (font! c-context font))
        (when fill-text (fill-text! c-context fill-text))
        (when fill-style (fill-style! c-context fill-style))
        (when begin-path (begin-path! c-context))
        (when close-path (close-path! c-context))
        (when arc (arc! c-context arc))
        (when fill (fill! c-context))
        (when set-last-render-time (set-last-render-time! set-last-render-time render-state))
        (when set-last-frame-time (set-last-frame-time! set-last-frame-time render-state))
        (when add-shot-blob (add-shot-blob! add-shot-blob game-state))
        (when set-game-state (set-game-state! set-game-state game-state))
        (when update-entity (update-entity! update-entity game-state))))

    (doseq [mut mutation]
      (mutator! mut game-state render-state))))

(defn shoot! [shoot-position game-state render-state]
  (println shoot-position)
  (let [entity (-> @game-state :entities (get (:entity-id @game-state)))]
    (-> (game/shoot {:shoot-position   shoot-position
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
                              :is-rendering-debug is-rendering-debug})
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
  (input/init! {:canvas   (:canvas @render-state)
                :shoot-fn (fn [shoot-position] (shoot! shoot-position game-state render-state))}))

(defn mk-initial-game-state [canvas]
  (atom {:entities  {1 {:id 1
                        :x  100
                        :y  150
                        :vx 0
                        :vy 0
                        :r  50}}
         :width     (.-width canvas)
         :height    (.-height canvas)
         :entity-id 1}))

(defn mk-initial-render-state [canvas c-context]
  (atom {:last-render-time           nil
         :last-frame-time            nil
         :is-rendering-debug         true
         :is-frame-throttling        false
         :frame-throttling-threshold 10000
         :canvas                     canvas
         :c-context                  c-context}))

(defn main! []
  (let [canvas (.getElementById js/document "canvas")
        c-context (.getContext canvas "2d")
        game-state (mk-initial-game-state canvas)
        render-state (mk-initial-render-state canvas c-context)]

    (def x-game-state game-state)
    (def x-render-state render-state)

    (enable-console-print!)
    (start! game-state render-state)))


(comment
  (update-entity! {:id 1, :x 150.00000000000009, :y 200, :vx 0, :vy 0, :r 143.2917960675007}
                  x-game-state)
  (add-entity! {:x 428.92762424715266, :y 275.66520372745936, :vx 2.346329953009422, :vy -0.42281901682986395, :r 6.408203932499373, :id 2}
               x-game-state)
  (add-shot-blob!
                  x-game-state))