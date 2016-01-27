(defproject org.apraxis/apraxis "0.0.1"
  :description "The Apraxis Application Framework"
  :url "github.com/apraxis/apraxis"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :resource-paths ["resources"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.jruby/jruby-complete "9.0.4.0"]
                 [io.pedestal/pedestal.service "0.4.1" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [cheshire "5.5.0"]
                 [enlive "1.1.6"]
                 [clojure-watch "0.1.11"]
                 [com.stuartsierra/component "0.3.1"]
                 [figwheel "0.5.0-5" :exclusions [org.clojure/tools.reader]]
                 [figwheel-sidecar "0.5.0-5" :exclusions [org.clojure/tools.reader]]
                 [clojurescript-build "0.1.9"]
                 [kioo "0.4.1" :exclusions [org.clojure/tools.reader com.keminglabs/cljx]]])
