(defproject org.apraxis/apraxis "0.0.1-SNAPSHOT"
  :description "The Apraxis Application Framework"
  :url "github.com/apraxis/apraxis"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :resource-paths ["resources"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.jruby/jruby-complete "1.7.19"]
                 [io.pedestal/pedestal.service "0.3.1" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [cheshire "5.4.0"]
                 [enlive "1.1.5"]]
  :eval-in-leiningen true)
