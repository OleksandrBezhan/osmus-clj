(ns backend.server
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [sc.api]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources files]]
            [compojure.handler :refer [api]]
            [ring.util.http-response :refer :all]
            [ring.middleware.defaults]
            [org.httpkit.server :refer [run-server]]
            [backend.pages :refer [index-page devcards-page]]))

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
           (GET "/devcards" []
             (-> (ok devcards-page) (content-type "text/html"))))

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

(defn new-system [opts]
  (component/system-map
    :http-kit (map->HttpKit opts)))