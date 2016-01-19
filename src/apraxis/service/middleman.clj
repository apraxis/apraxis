(ns apraxis.service.middleman
  (:require [apraxis.util.jruby :as jr]
            [apraxis.client.template :as template]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)])
  (:import java.io.ByteArrayInputStream))

(defn response
  [{:keys [invoker adapter] :as middleman} path]
  (.callMethod invoker adapter "response" (to-array [path])))

(defn raw-response
  [{:keys [invoker adapter] :as middleman} path]
  (.callMethod invoker adapter "raw_response" (to-array [path])))

(defn build
  [middleman]
  (jr/with-target-root (:target-dir middleman)
    (jr/run-middleman-build)))

(defrecord Middleman
    [target-dir invoker adapter build-completed]
  Lifecycle
  (start [this]
    (jr/with-target-root (:target-dir this)
      (jr/ensure-bundler)
      (jr/ensure-middleman)
      (let [[invoker adapter] (jr/make-middleman-adapter)]
        (assoc this
          :invoker invoker
          :adapter adapter))))
  (stop [this]
    this)
  template/HtmlResourceProvider
  (html-stream [this component-name]
    (ByteArrayInputStream. (.getBytes (response this (format "/structure/components/%s/index.html" component-name))))))
