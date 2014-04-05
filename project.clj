(defproject priork "0.0.0-SNAPSHOT"
  :description "Prioritize work"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [clj-redis "0.0.12"]
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [ring-basic-authentication "1.0.5"]]

  :plugins [[lein-cljsbuild "0.3.0"]]
  :hooks [leiningen.cljsbuild]
  
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:output-to "public/core.js"}}]}

  :min-lein-version "2.0.0")
