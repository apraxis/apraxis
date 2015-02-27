(ns apraxis.test-build
  (:require [apraxis.util.jruby :as jr]))

(defn test-build
  []
  (let [target-dir "target"]
    (jr/ensure-middleman-env target-dir)
    (jr/ensure-bundler target-dir)
    (jr/ensure-middleman target-dir)
    (jr/run-middleman-build target-dir)))
