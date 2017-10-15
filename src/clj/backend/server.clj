(ns backend.server
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [sc.api]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources files]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :refer :all]
            [ring.middleware.defaults]
            [org.httpkit.server :refer [run-server]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [backend.index :refer [index-page test-page]]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:user-id-fn #(:client-id %)})]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids)                       ; Watchable, read-only atom
  )

(defroutes ring-routes
           ;; Note: when running uberjar from project dir, it is
           ;; possible that this dir exists.
           (if (.exists (io/file "dev-output/js"))
             (files "/js" {:root "dev-output/js"})
             (resources "/js" {:root "js"}))

           (if (.exists (io/file "dev-output/css"))
             (files "/css" {:root "dev-output/css"})
             (resources "/css" {:root "css"}))

           (GET "/" []
             ; Use (resource-response "index.html") to serve index.html from classpath
             (-> (ok index-page) (content-type "text/html")))
           (GET "/test" []
             (-> (ok test-page) (content-type "text/html")))

           (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
           (POST "/chsk" req (ring-ajax-post req))
           )

(def main-ring-handler
  (ring.middleware.defaults/wrap-defaults
    ring-routes ring.middleware.defaults/site-defaults))

(defrecord HttpKit [port reload reload-dirs]
  component/Lifecycle
  (start [this]
    (let [port (or port 10555)]
      (println (str "Starting web server on http://localhost:" port))
      (assoc this :http-kit (run-server (var backend.server/main-ring-handler)
                                        {:port port :join? false}))))
  (stop [this]
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (assoc this :http-kit nil)))

(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when-let [new-uid (-> (set/difference (:any new) (:any old)) (seq))]
               (println "New connected uid:" new-uid))))

(defmulti -event-msg-handler
          "Multimethod to handle Sente `event-msg`s"
          :id ; Dispatch on event-id
          )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg) ; Handle event-msgs on a single thread
  )

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println "Unhandled event:" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod -event-msg-handler :osmus/join
  [{:as ev-msg :keys [uid send-fn]}]
  (chsk-send! uid [:osmus/state "State"]))

(defmethod -event-msg-handler :chsk/uidport-open
  [{:keys [client-id] :as ev-msg}])

(defmethod -event-msg-handler :chsk/uidport-close [_] ())
(defmethod -event-msg-handler :chsk/ws-ping [_] ())

(defonce ws-router_ (atom nil))
(defn  stop-ws-router! [] (when-let [stop-fn @ws-router_] (stop-fn)))
(defn start-ws-router! []
  (stop-ws-router!)
  (reset! ws-router_
          (sente/start-server-chsk-router!
            ch-chsk event-msg-handler)))

(defn new-system [opts]
  (component/system-map
    :http-kit (map->HttpKit opts)))

(comment
  (require '[reloaded.repl :refer [reset]])
  (reset)
  (start-ws-router!)
  (sc.api/defsc -2)
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid [:osmus/test "test message"])))