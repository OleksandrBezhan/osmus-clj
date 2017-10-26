(ns frontend.devcards
  (:require
    [devcards.core :as dc]
    [frontend.gui :as gui])
  (:require-macros
    [devcards.core :refer [defcard deftest defcard-rg defcard-doc]]))

(def canvas (.createElement js/document "canvas"))

(defn c-context [canvas] (.getContext canvas "2d"))

(defcard my-first-card
         [:div "Hello"])

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")

  (dc/start-devcard-ui!*))