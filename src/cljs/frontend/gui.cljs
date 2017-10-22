(ns frontend.gui
  (:require [common.hello :refer [foo-cljc]]
            [frontend.input :as input]
            [common.game :as game]
            [foo.bar]
            [cljs.pprint :as pprint]))

(defonce game-state (atom {:entities {1 {:id 1
                                         :x  150
                                         :y  200
                                         :vx 0
                                         :vy 0
                                         :r  100}
                                      2 {:id 2
                                         :x  200
                                         :y  290
                                         :vx 0
                                         :vy 0
                                         :r  50}}}))

(def render-state (atom {:last-render-time nil
                         :last-frame-time  nil}))


(def is-rendering-debug true)
(def is-frame-throttling false)
(def frame-throttling-threshold 10000)

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

(defn render-entity [entity]
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

(defn render-entities [{:keys [delta width height entities]}]
  [{:clear-rect {:x1 0 :y1 0 :x2 width :y2 height}}
   (for [entity entities]
     (render-entity entity))])

(defn render-fps [delta]
  (when (> delta 0)
    (let [fps (-> (/ 1 delta) (* 1000))]
      [{:fill-style "Black"}
       {:font "normal 16pt Arial"}
       {:fill-text {:text (str (int fps) " fps")
                    :x    10
                    :y    26}}])))

;; side effects
(declare canvas)
(declare c-context)

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

(defn set-last-render-time! [time]
  (swap! render-state assoc :last-render-time time))

(defn set-last-frame-time! [time]
  (swap! render-state assoc :last-frame-time time))

(defn font! [c-context font]
  (aset c-context "font" font))

(defn add-entity! [entity game-state]
  (swap! game-state update :entities assoc (:id entity) entity))

(defn add-shot-blob! [blob game-state]
  (add-entity! blob game-state))

(defn gen-entity-id! []
  (let [id (->> @game-state :entities keys (apply max) (+ 1))]
    (add-entity! {:id id} game-state)
    id))

(defn update-entity! [entity game-state]
  (swap! game-state update :entities assoc (:id entity) entity))

(defn set-game-state! [next-game-state game-state]
  (reset! game-state next-game-state))

(defn mutator!
  [mutation]
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
                    set-game-state]} mutation]
        (when clear-rect (clear-rect! c-context clear-rect))
        (when font (font! c-context font))
        (when fill-text (fill-text! c-context fill-text))
        (when fill-style (fill-style! c-context fill-style))
        (when begin-path (begin-path! c-context))
        (when close-path (close-path! c-context))
        (when arc (arc! c-context arc))
        (when fill (fill! c-context))
        (when set-last-render-time (set-last-render-time! set-last-render-time))
        (when set-last-frame-time (set-last-frame-time! set-last-frame-time))
        (when add-shot-blob (add-shot-blob! add-shot-blob game-state))
        (when set-game-state (set-game-state! set-game-state game-state))
        (when update-entity (update-entity! update-entity game-state))))

    (doseq [mut mutation]
      (mutator! mut))))

(defn shoot! [args]
  (-> (game/shoot (assoc args :gen-entity-id-fn gen-entity-id!))
      (mutator!)))

(defn render-frame [time render-state]
  (let [frame-throttling-delta (- time (:last-render-time @render-state))]
    (if (or (not is-frame-throttling) (and is-frame-throttling (> frame-throttling-delta frame-throttling-threshold)))
      (let [delta (game/compute-delta (:last-frame-time @render-state) time)
            next-game-state (game/compute-game-state {:delta delta :game-state @game-state})]
        (-> (render-entities {:width    (:width next-game-state)
                              :height   (:height next-game-state)
                              :entities (vals (:entities next-game-state))
                              :delta    delta})
            (conj (when is-rendering-debug (render-fps delta)))
            (conj {:set-last-render-time time
                   :set-last-frame-time  time
                   :set-game-state       next-game-state})))

      {:set-last-frame-time time})))

(defn render-frame! [time]
  (-> (render-frame time render-state)
      (mutator!)
      ((fn [_] (request-animation-frame! render-frame!)))))

(defn start! []
  (js/console.log "Starting the app")
  (def canvas (.getElementById js/document "canvas"))
  (def c-context (.getContext canvas "2d"))
  (swap! game-state (fn [state-v] (-> state-v (assoc :width (.-width canvas) :height (.-height canvas)))))
  (request-animation-frame! render-frame!)
  )

(defn main! []
  (enable-console-print!)
  (start!)
  (input/init! {:shoot-fn      shoot!
                :get-entity-fn #(-> @game-state :entities vals first)})
  ;(let [{:keys [start]} (ws/init!)] (start))
  )

(main!)

(comment
  (deref game-state)
  (println "foo"))