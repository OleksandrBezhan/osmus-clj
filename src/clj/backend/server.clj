(ns backend.server
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
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

(defn new-system [opts]
  (component/system-map
    :http-kit (map->HttpKit opts)))

(comment
  (require 'sc.api)
  (require '[reloaded.repl :refer [reset]])
  (reset)
  (sc.api/defsc 9))