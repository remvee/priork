(defproject priork "0.0.0-SNAPSHOT"
  :description "Prioritize work"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [compojure "0.6.5"]
                 [hiccup "0.3.6"]
                 [ring/ring-jetty-adapter "0.3.11"]
                 [ring/ring-devel "0.3.11"]
                 [clj-redis "0.0.12"]
                 [amalloy/ring-gzip-middleware "0.1.0"]])
