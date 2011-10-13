(ns priork.core
  (:require [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-helpers]
            [clj-redis.client :as redis]
            [ring.adapter.jetty :as jetty])
  (:use [compojure.core]
        [ring.middleware.params :only [wrap-params]]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.file-info :only [wrap-file-info]]
        [ring.middleware.gzip :only [wrap-gzip]]))

;;;;;;;;;;;;;;;;;;;;
;; Model

(def ^:dynamic *project* "priork")

(defn gen-id []
  (str "task-" (java.util.UUID/randomUUID)))

(def db (redis/init {:url (clojure.core/get (System/getenv)
                                            "REDISTOGO_URL" ; for running on Heroku
                                            "redis://localhost:6379/")}))

(def data (atom (or (if-let [val (redis/get db "data")]
                      (with-in-str val (read)))
                    {})))

(def backup-agent (agent nil))

(defn swap-tasks! [f & args]
  (dosync (swap! data #(update-in %
                                  [(or *project* "/")]
                                  (fn [v] (apply f (or v []) args))))
          (send-off backup-agent
                    (fn [_] (redis/set db "data" (with-out-str (prn @data)))))))

(defn tasks []
  (@data (or *project* "/")))

(defn task-by-id [id]
  (first (filter #(= (:id %) id) (tasks))))

;;;;;;;;;;;;;;;;;;;;
;; View

(def title "Priork")

(def h hiccup/h)

(defn layout [f & args]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (str (hiccup-helpers/html5
               [:head
                [:title (h (if *project* (str *project* "-" title) title))]
                (hiccup-helpers/include-css "/screen.css")
                [:meta {:name "viewport"
                        :content "width=device-width, initial-scale=1, maximum-scale=1"}]]
               [:body
                [:div#container
                 [:header
                  [:h1 (h title)]
                  [:h2 (h *project*)]]
                 [:div#body                              
                  (apply f args)]]
                (hiccup-helpers/include-js "/jquery.js" "/jquery-ui.js" "/app.js")]))})

(defn html-task [task]
  [:li.task {:id (:id task)}
   [:a.update (h (:text task))]
   [:form.remove {:action "delete" :method "post"}
    [:input {:type "hidden" :name "id" :value (:id task)}]
    [:button {:type "submit" :onclick "return confirm('Sure?')"} "&times;"]]])

(defn html-index []
  [:div
   [:ul.tasks
    (map html-task (tasks))]
   [:form.new-task {:action "create" :method "post"}
    [:input.focus {:type "text" :autocomplete "off" :name "task"}]]])

;;;;;;;;;;;;;;;;;;;;
;; Controller

(defn xhr? [request]
  (= ((:headers request) "x-requested-with") "XMLHttpRequest"))

(defn redirect-to-index []  
  {:status 302 :headers {"Location" (if *project* (str "/" *project* "/") "/")}})

(defroutes handler
  (GET "/" []
       (layout html-index))
  (POST "/create" [task]
        (when-not (= (count task) 0)
          (swap-tasks! conj {:id (gen-id), :text task}))
        (redirect-to-index))
  (POST "/update" {{id "id" task "task"} :params :as request}
        (let [task {:id (gen-id) :text task}]
          (swap-tasks! (fn [x] (vec (replace {(task-by-id id) task} x))))
          (if (xhr? request)
            (hiccup/html (html-task task))
            (redirect-to-index))))
  (POST "/delete" [id]
        (swap-tasks! (fn [x] (vec (filter #(not= (:id %) id) x))))
        (redirect-to-index))
  (POST "/order" {{ids "ids[]"} :params}
        (swap-tasks! (fn [_] (vec (filter identity (map task-by-id ids)))))
        {:status 200})
  (ANY "*" {uri :uri}
       (if-not (re-matches #".*/$" uri)
         {:status 302 :headers {"Location" (str uri "/")}})))

(defn wrap-project [app]
  (fn [req]
    (binding [*project* (last (re-matches #"/(.*?)/.*" (:uri req)))]
      (app (assoc req :uri (if *project*
                             (subs (:uri req) (inc (count *project*)))
                             (:uri req)))))))

(def app (-> handler
             wrap-project
             wrap-params
             wrap-gzip
             (wrap-file "public")
             wrap-file-info))

;;;;;;;;;;;;;;;;;;;;
;; Running for running on Heroku
(defn -main []
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (jetty/run-jetty (var app) {:port port})))
