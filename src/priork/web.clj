(ns priork.web
  (:require [priork.utils :as utils]
            [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-helpers]
            [ring.adapter.jetty :as jetty])
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

(def h hiccup/h)

(defn layout [f & args]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (str (hiccup-helpers/html5
               [:head
                [:title (h title)]
                (hiccup-helpers/include-css "/screen.css")
                [:meta {:name "viewport"
                        :content "width=device-width, initial-scale=1, maximum-scale=1"}]]
               [:body
                [:div#container
                 [:h1 (h title)]
                 [:div#body                              
                  (apply f args)]]
                (hiccup-helpers/include-js "/jquery.js" "/jquery-ui.js" "/app.js")]))})

(defn html-task [task]
  [:li.task {:id (:id task)}
   (h (:text task))
   [:form.remove {:action "/remove" :method "post"}
    [:input {:type "hidden" :name "id" :value (:id task)}]
    [:button {:type "submit" :onclick "return confirm('Sure?')"} "&times;"]]])

(defn html-index []
  [:div
   [:ul.tasks
    (map html-task @tasks)]
   [:form.new-task {:action "/add" :method "post"}
    [:input.focus {:type "text" :name "task"}]]])

(defroutes handler
  (GET "/" []
       (layout html-index))
  (POST "/add" [task]
        (when-not (= (count task) 0)
          (swap! tasks conj {:id (utils/sha1 task), :text task}))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/remove" [id]
        (swap! tasks (fn [tasks] (vec (filter #(not= (:id %) id) tasks))))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/reorder" {{ids "ids[]"} :params}
        (swap! tasks (fn [_] (vec (filter identity (map task-by-id ids)))))
        {:status 200}))

(def app (-> handler
             wrap-params
             (wrap-file "public")
             wrap-file-info))

(defn -main []
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (jetty/run-jetty app {:port port})))
