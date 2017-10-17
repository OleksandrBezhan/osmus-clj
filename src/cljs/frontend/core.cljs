(ns frontend.core
  (:require-macros [frontend.macro :refer [foobar]])
  (:require [common.hello :refer [foo-cljc]]
            [foo.bar]
            [taoensso.sente :as sente]
            [taoensso.encore :as encore :refer-macros (have have?)]))

(enable-console-print!)

(def pi (-> js/Math .-PI))

;; WS
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"                   ; Note the same path as before
                                  {:type :auto              ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

;; Reagent application state
;; Defonce used to that the state is kept between reloads
;(defonce app-state (atom {:y 2017}))

(defonce game-state (atom {:entities {1 {:x 100
                                         :y 200
                                         :r 100
                                         }}}))

(defn join []
  (println "Sending osmus/join")
  (chsk-send! [:osmus/join {:had-a-callback? "nope"}]))

(defn shoot [])

(defn clear-rect! [c-context {:keys [x1 y1 x2 y2]}]
  (.clearRect c-context x1 y1 x2 y2))

(def request-animation-frame
  (or (.-requestAnimationFrame js/window)
      (.-webkitRequestAnimationFrame js/window)
      (.-mozRequestAnimationFrame js/window)
      (.-oRequestAnimationFrame js/window)
      (.-msRequestAnimationFrame js/window)
      (fn [callback] (js/setTimeout callback (/ 1000 60)))))


(defmulti -event-msg-handler
          "Multimethod to handle Sente `event-msg`s"
          :id                                               ; Dispatch on event-id
          )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [ev-msg]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default                                                  ; Default/fallback case (no other matching handler)
  [{:keys [event]}]
  ;(println "Unhandled event:" event)
  )

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  (let [[msg-id msg] ?data]
    (condp = msg-id
      :osmus/state (do
                     ;(println "recv state" msg)
                     ;(reset! game-state msg)
                     )
      (println "received unknown msg" ?data)))
  )

(defonce ws-router_ (atom nil))
(defn stop-ws-router! [] (when-let [stop-f @ws-router_] (stop-f)))
(defn start-ws-router! []
  (stop-ws-router!)
  (reset! ws-router_
          (sente/start-client-chsk-router!
            ch-chsk event-msg-handler)))

(defn render-handler [{:keys [width height entities]}]
  [{:clear-rect {:x1 0 :y1 0 :x2 width :y2 height}}
   (for [entity entities]
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
      {:fill true}])])

(declare canvas)
(declare c-context)
(declare c-height)
(declare c-width)

(defn fill-style! [c-context color]
  (aset c-context "fillStyle" color))

(defn begin-path! [c-context]
  (.beginPath c-context))

(defn close-path! [c-context]
  (.closePath c-context))

(defn arc! [c-context {:keys [x y r start-angle end-angle is-anticlockwise]}]
  (.arc c-context x y r start-angle end-angle is-anticlockwise))

(defn fill! [c-context]
  (.fill c-context))

(defn mutator!
  [mutation]
  (if (map? mutation)
    (do
      (let [{:keys [clear-rect
                    fill-style
                    begin-path
                    close-path
                    arc
                    fill]} mutation]
        (when clear-rect (clear-rect! c-context clear-rect))
        (when fill-style (fill-style! c-context fill-style))
        (when begin-path (begin-path! c-context))
        (when close-path (close-path! c-context))
        (when arc (arc! c-context arc))
        (when fill (fill! c-context))))

    (doseq [mut mutation]
      (mutator! mut))))

(defn start! []
  (js/console.log "Starting the app")
  (def canvas (.getElementById js/document "canvas"))
  (def c-context (.getContext canvas "2d"))
  (def c-height (.-height canvas))
  (def c-width (.-width canvas))
  (request-animation-frame (fn [time]
                             (-> (render-handler {:width    c-width
                                                  :height   c-height
                                                  :entities (vals (:entities @game-state))})
                                 (mutator!))))
  )

;; When this namespace is (re)loaded the Reagent app is mounted to DOM
(start!)

(start-ws-router!)

;; Macro test
;(foobar :abc 3)
;; Example of interop call to plain JS in src/cljs/foo.js
;(js/foo)

(comment
  (println "foo"))