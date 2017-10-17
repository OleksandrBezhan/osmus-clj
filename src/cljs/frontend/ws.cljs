(ns frontend.ws
  (:require [common.hello :refer [foo-cljc]]
            [frontend.input :as input]
            [common.game :as game]
            [taoensso.sente :as sente]
            [taoensso.encore :as encore :refer-macros (have have?)]))

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

(declare ws-router)
(declare channel-socket)
(declare ch-chsk)
(declare chsk-send!)
(declare chsk-state)

(defn init! []
  (defonce channel-socket (sente/make-channel-socket! "/chsk" {:type :auto}))

  (let [{:keys [chsk ch-recv send-fn state]} channel-socket]
    (defonce ws-router (atom nil))
    (defonce chsk chsk)
    (defonce ch-chsk ch-recv)
    (defonce chsk-send! send-fn)
    (defonce chsk-state state)

    (let [stop-ws-router! (fn [] (when-let [stop-f @ws-router] (stop-f)))
          start-ws-router! (fn []
                             (stop-ws-router!)
                             (reset! ws-router
                                     (sente/start-client-chsk-router!
                                       ch-chsk event-msg-handler)))]
      {:start      start-ws-router!})))