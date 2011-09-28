(ns priork.web
  (:require [priork.utils :as utils]
            [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-helpers])
  (:use [compojure.core]
        [ring.middleware.params :only [wrap-params]]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.file-info :only [wrap-file-info]]))

(def title "Priork")

(def tasks (atom (vec (map (fn [x] {:id (utils/sha1 x) :text x})
                           ["feed the cat"
                            "water the plants"]))))

(defn task-by-id [id]
  (first (filter #(= (:id %) id) @tasks)))

(defn layout [f & args]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (str "<!DOCTYPE HTML>"
              (hiccup/html
               [:html
                [:head
                 [:title title]
                 (hiccup-helpers/include-css "/screen.css")]
                [:body
                 [:header
                  [:h1 title]]
                 [:div.body                              
                  (apply f args)]
                 [:footer]
                 (hiccup-helpers/include-js "/jquery.js" "/jquery-ui.js" "/app.js")]]))})

(defn index []
  [:div
   [:ul.tasks
    (map #(vec [:li.task {:id (:id %)} (:text %)]) @tasks)]
   [:form.new-task {:action "/add", :method "post"}
    [:input.focus {:type "text", :name "task"}]]])

(defroutes handler
  (GET "/" []
       (layout index))
  (POST "/add" [task]
        (when-not (= (count task) 0)
          (swap! tasks conj {:id (utils/sha1 task), :text task}))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/reorder" {{ids "ids[]"} :params}
        (let [new-order (vec (filter identity (map task-by-id ids)))]
          (reset! tasks new-order))
        {:status 200}))

(def app (-> handler
             wrap-params
             (wrap-file "public")
             wrap-file-info))

;; (do (require 'ring.adapter.jetty) (ring.adapter.jetty/run-jetty (var app) {:port 8080}))
