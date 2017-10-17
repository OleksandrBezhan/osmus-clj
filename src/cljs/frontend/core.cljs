(ns frontend.core
  (:require-macros [frontend.macro :refer [foobar]])
  (:require [common.hello :refer [foo-cljc]]
            [frontend.input :as input]
            [common.game :as game]
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

(defonce game-state (atom {:entities {1 {:id 1
                                         :x  150
                                         :y  200
                                         :vx 0
                                         :vy 0
                                         :r  100
                                         }}}))

(defn join []
  (println "Sending osmus/join")
  (chsk-send! [:osmus/join {:had-a-callback? "nope"}]))

(defn shoot [])

(defn clear-rect! [c-context {:keys [x1 y1 x2 y2]}]
  (.clearRect c-context x1 y1 x2 y2))

(defn request-animation-frame [handler]
  (.requestAnimationFrame js/window handler))

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

(defn render-handler [{:keys [delta c-width c-height entities]}]
  [{:clear-rect {:x1 0 :y1 0 :x2 c-width :y2 c-height}}
   (for [entity entities]
     (let [computed-entity (game/compute-position {:entity entity :delta delta})
           args {:entity computed-entity :game-width c-width :game-height c-height}
           next-entity (if (game/in-bounds args) computed-entity (game/reposition-in-bounds args))]
       [{:fill-style "green"}
        {:begin-path true}
        {:arc {
               :x                (:x next-entity)
               :y                (:y next-entity)
               :r                (:r next-entity)
               :start-angle      0
               :end-angle        (* 2 pi)
               :is-anticlockwise true}}
        {:close-path true}
        {:fill true}
        {:update-entity next-entity}]))])

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

(defn update-entity! [{:keys [id] :as entity} game-state]
  (swap! game-state (fn [state-v]
                      (assoc-in state-v [:entities id] entity))))

;; mutations
(def render-state (atom {:last-render-time nil}))

(defn set-last-render-time! [time]
  (swap! render-state assoc :last-render-time time))

(defn mutator!
  [mutation]
  (if (map? mutation)
    (do
      (let [{:keys [clear-rect
                    fill-style
                    begin-path
                    close-path
                    arc
                    fill
                    set-last-render-time
                    update-entity]} mutation]
        (when clear-rect (clear-rect! c-context clear-rect))
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

(defn render-frame [time]
  (let [delta (compute-delta (:last-render-time @render-state) time)]
    (-> (render-handler {:c-width  c-width
                         :c-height c-height
                         :entities (vals (:entities @game-state))
                         :delta    delta})
        (conj {:set-last-render-time time})
        (mutator!))
    (request-animation-frame render-frame)))

(defn start! []
  (js/console.log "Starting the app")
  (def canvas (.getElementById js/document "canvas"))
  (def c-context (.getContext canvas "2d"))
  (def c-height (.-height canvas))
  (def c-width (.-width canvas))
  (request-animation-frame render-frame))

;; When this namespace is (re)loaded the Reagent app is mounted to DOM
(start!)

(input/init! {:shoot-fn      shoot!
              :get-entity-fn #(-> @game-state :entities vals first)})
(start-ws-router!)

;; Macro test
;(foobar :abc 3)
;; Example of interop call to plain JS in src/cljs/foo.js
;(js/foo)

(comment
  (println "foo"))