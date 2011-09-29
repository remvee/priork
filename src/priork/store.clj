(ns priork.store
  (:refer-clojure :exclude [get set])
  (:require [clj-redis.client :as redis]))

(def url (clojure.core/get (System/getenv)
                           "REDISTOGO_URL" ; for running on Heroku
                           "redis://localhost:6379/"))
(def db (redis/init {:url url}))

(defn set [key val]
  (redis/set db (str key) (with-out-str (prn val))))

(defn get [key]
  (if-let [val (redis/get db (str key))]
    (with-in-str val (read))))
