(ns apraxis.service.middleman
  (:require [apraxis.util.jruby :as jr]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]))

(defrecord Middleman
    [target-dir]
  Lifecycle
  (start [this]
    (jr/with-target-root (:target-dir this)
      (jr/ensure-bundler)
      (jr/ensure-middleman))
    this)
  (stop [this]
    this))

(defn build
  [middleman]
  (jr/with-target-root (:target-dir middleman)
    (jr/run-middleman-build)))
