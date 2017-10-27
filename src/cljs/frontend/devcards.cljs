(ns frontend.devcards
  (:require
    [devcards.core :as dc]
    [reagent.core :as reagent]
    [frontend.gui :as gui])
  (:require-macros
    [devcards.core :refer [defcard deftest defcard-rg defcard-doc]]))


(defn draw-canvas [canvas]
  (let [ctx (.getContext canvas "2d")]
    (gui/start! canvas ctx)))

(defn div-with-canvas []
  (let [dom-node (reagent/atom nil)]
    (reagent/create-class
      {:component-did-update
       (fn [this]
         (println "component did update")
         (draw-canvas (.-firstChild @dom-node)))
       :component-did-mount
       (fn [this]
         (println "component did mount")
         (reset! dom-node (reagent/dom-node this)))
       :component-will-unmount
       (fn [this]
         (println "component will unmount"))
       :reagent-render
       (fn []
         [:div.with-canvas
          [:canvas (if-let [node @dom-node]
                     {:width 300
                      :height 300})]])})))

(defn canvas-component []
  [div-with-canvas])

(defcard-rg canvas-component-card
            "A reagent component with a canvas"
            canvas-component)

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")

  (dc/start-devcard-ui!*))