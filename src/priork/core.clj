;; Copyright (c) Remco van 't Veer. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution.  By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license.  You must not remove this notice, or any other, from
;; this software.

(ns priork.core
  (:require [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-helpers]
            [clj-redis.client :as redis]
            [ring.adapter.jetty :as jetty])
  (:use [compojure.core]
        [ring.middleware.params :only [wrap-params]]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.file-info :only [wrap-file-info]]
        [ring.middleware.gzip :only [wrap-gzip]]
        [ring.middleware.basic-authentication :only [wrap-basic-authentication]])
  (:import [java.net URLEncoder URLDecoder]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;
;; Model

(def ^:dynamic *project* nil)

(defn gen-id []
  (str "task-" (UUID/randomUUID)))

(def db (redis/init {:url (get (System/getenv)
                               "REDISTOGO_URL" ; for running on Heroku
                               "redis://localhost:6379/")}))

(def data (atom (or (if-let [val (redis/get db "data")]
                      (with-in-str val (read)))
                    {})))

(def backup-agent (agent nil))

(defn swap-tasks! [f & args]
  (dosync (swap! data
                 #(into {}
                        (filter (fn [[k v]] (or (nil? k)
                                                (not (empty? v))))
                                (update-in %
                                           [*project*]
                                           (fn [v] (apply f v args))))))
          (send-off backup-agent
                    (fn [_] (redis/set db "data" (with-out-str (prn @data)))))))

(defn projects []
  (sort (keys @data)))

(defn tasks []
  (get @data *project*))

(defn task-by-id [tasks id]
  (first (filter #(= (:id %) id) tasks)))

(defn wrap-project [app]
  (fn [req]
    (let [path (last (re-matches #"/(.*?)/.*" (:uri req)))]
      (binding [*project* (and path (URLDecoder/decode path))]
        (app (assoc req :uri (if *project*
                               (subs (:uri req) (inc (count path)))
                               (:uri req))))))))

;;;;;;;;;;;;;;;;;;;;
;; View

(def title "Priork")

(def h hiccup/h)

(declare project-path)

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
                  (apply f args)]
                 [:footer
                  [:ul.projects
                   (map (fn [project]
                          [:li [:a {:href (project-path project)}
                                (if project (h project) "&uarr;")]])
                        (projects))]]]
                (hiccup-helpers/include-js "/jquery.min.js"
                                           "/jquery-ui.min.js"
                                           "/core.js")]))})

(defn html-task [task]
  [:li.task {:id (:id task)}
   [:a.update (h (:text task))]
   [:form.remove {:action "delete" :method "post"}
    [:input {:type "hidden" :name "id" :value (:id task)}]
    [:button {:type "submit" :onclick "return confirm('Sure?')"} "&times;"]]])

(defn html-index []
  [:div
   [:form.new-task {:action "create" :method "post"}
    [:input.focus {:type "text" :autocomplete "off" :name "task"}]]
   [:ul.tasks
    (map html-task (tasks))]])

;;;;;;;;;;;;;;;;;;;;
;; Controller

(defn project-path [project]
  (if project (str "/" (URLEncoder/encode project) "/") "/"))

(defn xhr? [request]
  (= ((:headers request) "x-requested-with") "XMLHttpRequest"))

(defn redirect-to-index []  
  {:status 302 :headers {"Location" (project-path *project*)}})

(defroutes handler
  (GET "/" []
       (layout html-index))
  (POST "/create" [task]
        (when-not (= (count task) 0)
          (swap-tasks! conj {:id (gen-id), :text task}))
        (redirect-to-index))
  (POST "/update" {{id "id" task "task"} :params :as request}
        (let [task {:id (gen-id) :text task}]
          (swap-tasks! #(replace {(task-by-id % id) task} %))
          (if (xhr? request)
            (hiccup/html (html-task task))
            (redirect-to-index))))
  (POST "/delete" [id]
        (swap-tasks! (fn [x] (filter #(not= (:id %) id) x)))
        (redirect-to-index))
  (POST "/order" {{ids "ids[]"} :params}
        (swap-tasks! (fn [x] (filter identity
                                     (map #(task-by-id x %) ids))))
        {:status 200})
  (ANY "*" {uri :uri}
       (if-not (re-matches #".*/$" uri)
         {:status 302 :headers {"Location" (str uri "/")}})))

;;;;;;;;;;;;;;;;;;;;
;; Middleware

(defn auth [username password]
  (let [u (System/getenv "BASIC_AUTH_USERNAME")
        p (System/getenv "BASIC_AUTH_PASSWORD")]
    (or (and u p (= u username) (= p password))
        (not (or u p)))))

(defn wrap-force-ssl [app]
  (fn [req]
    (if (or (= "localhost" (:server-name req))
            (= :https (:scheme req))
            (= "https" ((:headers req) "x-forwarded-proto")))
      (app req)
      {:status 302
       :headers {"Location" (str "https://" (:server-name req) (:uri req))}})))

(def app (-> handler
             wrap-project
             wrap-params
             wrap-gzip
             (wrap-file "public")
             wrap-file-info
             (wrap-basic-authentication auth)
             wrap-force-ssl))

;;;;;;;;;;;;;;;;;;;;
;; Running for running on Heroku
(defn -main []
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (jetty/run-jetty (var app) {:port port})))
