(ns priork.web
  (:require [priork.store :as store]
            [priork.utils :as utils]
            [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-helpers]
            [ring.adapter.jetty :as jetty])
  (:use [compojure.core]
        [ring.middleware.params :only [wrap-params]]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.file-info :only [wrap-file-info]]
        [ring.middleware.gzip :only [wrap-gzip]]))

;;;;;;;;;;;;;;;;;;;;
;; Model

(def project "priork")

(def tasks (atom (or (store/get project) [])))

(def backup-agent (agent nil))

(defn swap-tasks! [f & args]
  (dosync (apply swap! tasks f args)
          (send-off backup-agent (fn [_] (store/set project @tasks)))))

(defn task-by-id [id]
  (first (filter #(= (:id %) id) @tasks)))

;;;;;;;;;;;;;;;;;;;;
;; View

(def title "Priork")

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
   [:a.edit (h (:text task))]
   [:form.remove {:action "delete" :method "post"}
    [:input {:type "hidden" :name "id" :value (:id task)}]
    [:button {:type "submit" :onclick "return confirm('Sure?')"} "&times;"]]])

(defn html-index []
  [:div
   [:ul.tasks
    (map html-task @tasks)]
   [:form.new-task {:action "create" :method "post"}
    [:input.focus {:type "text" :name "task"}]]])

;;;;;;;;;;;;;;;;;;;;
;; Controller

(defroutes handler
  (GET "/" []
       (layout html-index))
  (POST "/create" [task]
        (when-not (= (count task) 0)
          (swap-tasks! conj {:id (utils/sha1 task), :text task}))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/update" [id task]
        (swap-tasks! (fn [x] (vec (replace {(task-by-id id)
                                            {:id (utils/sha1 task) :text task}} x))))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/delete" [id]
        (swap-tasks! (fn [x] (vec (filter #(not= (:id %) id) x))))
        {:status 302
         :headers {"Location" "/"}})
  (POST "/order" {{ids "ids[]"} :params}
        (swap-tasks! (fn [_] (vec (filter identity (map task-by-id ids)))))
        {:status 200}))

(def app (-> handler
             wrap-params
             wrap-gzip
             (wrap-file "public")
             wrap-file-info))


;;;;;;;;;;;;;;;;;;;;
;; Running for running on Heroku
(defn -main []
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (jetty/run-jetty (var app) {:port port})))
