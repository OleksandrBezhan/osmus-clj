(ns frontend.core
  (:require [common.hello :refer [foo-cljc]]
    [frontend.input :as input]
    [frontend.ws :as ws]
            [common.game :as game]
            [foo.bar]))

(enable-console-print!)

(def pi (-> js/Math .-PI))

(defonce game-state (atom {:entities {1 {:id 1
                                         :x  150
                                         :y  200
                                         :vx 0
                                         :vy 0
                                         :r  100
                                         }}}))

(def render-state (atom {:last-render-time nil}))

(defn clear-rect! [c-context {:keys [x1 y1 x2 y2]}]
  (.clearRect c-context x1 y1 x2 y2))

(defn request-animation-frame [handler]
  (.requestAnimationFrame js/window handler))

(defn render-entity [entity]
  [{:fill-style "green"}
   {:begin-path true}
   {:arc {
          :x                (:x entity)
          :y                (:y entity)
          :r                (:r entity)
          :start-angle      0
          :end-angle        (* 2 pi)
          :is-anticlockwise true}}
   {:close-path true}
   {:fill true}])

(defn render-entities [{:keys [delta c-width c-height entities]}]
  [{:clear-rect {:x1 0 :y1 0 :x2 c-width :y2 c-height}}
   (for [entity entities]
     (let [next-entity (game/compute-entity-state {:entity      entity
                                                   :delta       delta
                                                   :game-width  c-width
                                                   :game-height c-height})]

       [(render-entity next-entity)
        {:update-entity next-entity}]))])

(declare canvas)
(declare c-context)
(declare c-height)
(declare c-width)

;; mutations
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

(defn update-entity! [{:keys [id] :as entity} game-state]
  (swap! game-state (fn [state-v]
                      (assoc-in state-v [:entities id] entity))))

(defn set-last-render-time! [time]
  (swap! render-state assoc :last-render-time time))

(defn font! [c-context font]
  (aset c-context "font" font))

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
                    update-entity
                    update-fps]} mutation]
        (when clear-rect (clear-rect! c-context clear-rect))
        (when font (font! c-context font))
        (when fill-text (fill-text! c-context fill-text))
        (when fill-style (fill-style! c-context fill-style))
        (when begin-path (begin-path! c-context))
        (when close-path (close-path! c-context))
        (when arc (arc! c-context arc))
        (when fill (fill! c-context))
        (when set-last-render-time (set-last-render-time! set-last-render-time))
        (when update-entity (update-entity! update-entity game-state))))

    (doseq [mut mutation]
      (mutator! mut))))

(defn shoot! [args]
  (-> (game/shoot args)
      (mutator!)))

(defn compute-delta [last-time? time]
  (if last-time?
    (- time last-time?)
    0))

(defn render-fps [delta]
  (when (> delta 0)
    (let [fps (-> (/ 1 delta) (* 1000))]
      [{:fill-style "Black"}
       {:font "normal 16pt Arial"}
       {:fill-text {:text (str (int fps) " fps")
                    :x    10
                    :y    26}}])))

(defn render-frame! [time]
  (let [delta (compute-delta (:last-render-time @render-state) time)]
    (-> (render-entities {:c-width  c-width
                          :c-height c-height
                          :entities (vals (:entities @game-state))
                          :delta    delta})
        (conj (render-fps delta))
        (conj {:set-last-render-time time})
        (mutator!)
        ((fn [_] (request-animation-frame render-frame!))))))

(defn start! []
  (js/console.log "Starting the app")
  (def canvas (.getElementById js/document "canvas"))
  (def c-context (.getContext canvas "2d"))
  (def c-height (.-height canvas))
  (def c-width (.-width canvas))
  (request-animation-frame render-frame!))

(defn run []
  (start!)

  (input/init! {:shoot-fn      shoot!
                :get-entity-fn #(-> @game-state :entities vals first)})

  (let [{:keys [start]} (ws/init!)]
    (start)))

(run)

(comment
  (println "foo"))