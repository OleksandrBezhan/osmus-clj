(ns frontend.devcards
  (:require
    [devcards.core :as dc]
    [reagent.core :as reagent]
    [frontend.gui :as gui])
  (:require-macros
    [devcards.core :refer [defcard deftest defcard-rg defcard-doc]]))


(defn div-with-canvas [draw-canvas-fn]
  (let [dom-node (reagent/atom nil)]
    (reagent/create-class
      {:component-did-update
       (fn [this]
         (draw-canvas-fn (.-firstChild @dom-node)))
       :component-did-mount
       (fn [this]
         (reset! dom-node (reagent/dom-node this)))
       :reagent-render
       (fn []
         [:div.with-canvas
          [:canvas (if-let [node @dom-node]
                     {:width  500
                      :height 400})]])})))

(defn canvas-component [draw-canvas-fn]
  [div-with-canvas draw-canvas-fn])

(defn draw-entity-with-enemies [canvas]
  (let [ctx (.getContext canvas "2d")
        game-state (gui/mk-initial-game-state canvas)
        enemies {2 {:id 2
                    :x  180
                    :y  120
                    :vx 0
                    :vy 0
                    :r  30
                    }
                 3 {:id 2
                    :x  200
                    :y  250
                    :vx 0
                    :vy 0
                    :r  49
                    }
                 4 {:id 4
                    :x  350
                    :y  230
                    :vx 0
                    :vy 0
                    :r  100}}
        render-state (gui/mk-initial-render-state canvas ctx)]
    (doseq [[id enemy] enemies]
      (swap! game-state assoc-in [:entities id] enemy))
    (gui/start! game-state render-state)))

(defcard-rg simple-entity-with-enemies-card
            "Entity with enemies"
            (canvas-component draw-entity-with-enemies))

(defn draw-entity-shoots [canvas]
  (let [ctx (.getContext canvas "2d")
        game-state (gui/mk-initial-game-state canvas)
        render-state (gui/mk-initial-render-state canvas ctx)]
    (gui/shoot! {:x 250 :y 150} game-state render-state)
    (-> (gui/render-frame 100 game-state render-state)
        (gui/mutator! game-state render-state))))

(defcard-rg entity-shoots-card
            "Entity shoots a blob"
            (canvas-component draw-entity-shoots))

(defn draw-entity-shoots-far-away [canvas]
  (let [ctx (.getContext canvas "2d")
        game-state (gui/mk-initial-game-state canvas)
        render-state (gui/mk-initial-render-state canvas ctx)]
    (gui/shoot! {:x 280 :y 150} game-state render-state)
    (-> (gui/render-frame 100 game-state render-state)
        (gui/mutator! game-state render-state))))

(defcard-rg entity-shoots-far-away-card
            "Entity shoots a blob far away"
            (canvas-component draw-entity-shoots-far-away))

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")

  (dc/start-devcard-ui!*))