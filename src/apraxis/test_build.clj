(ns apraxis.test-build
  (:require [apraxis.util.jruby :as jr]))

(defn test-build
  []
  (jr/with-target-root "target"
    (jr/ensure-bundler)
    (jr/ensure-middleman)
    (jr/run-middleman-build)))
