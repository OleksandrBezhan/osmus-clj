(ns backend.pages
  (:require
    [hiccup.core :refer [html]]
    [hiccup.page :refer [html5 include-js include-css]]))

(def index-page
  (html
    (html5
      [:head
       [:title "Osmus"]
       [:meta {:charset "utf-8"}]
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
       (include-css "css/main.css")]
      [:body
       [:div.container [:canvas#canvas {:width "640" :height "480"}]]
       (include-js "js/main.js")])))

(def devcards-page
  (html
    (html5
      [:head
       [:title "Devcards"]
       (include-css "css/main.css")]
      [:body
       [:h1 "Devcards"]
       (include-js "js/devcards.js")])))
