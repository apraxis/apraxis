(ns apraxis.service.file-monitor
  (:require [clojure.core.async :refer [put! chan]]
            [clojure-watch.core :refer [start-watch]]
            [io.pedestal.log :as log]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)])
  (:import (java.io File)))


(defn sample-notify
  [sample-subscriptions event filename]
  (doseq [subscriber (@sample-subscriptions filename)]
    (put! subscriber [event filename])))

(defn sample-subscribe
  [{:keys [sample-subscriptions] :as file-monitor} sample-file-name]
  (let [subscription (chan)]
    (swap! sample-subscriptions update-in [sample-file-name] (fnil conj []) subscription)
    subscription))

(defn sample-unsubscribe
  [{:keys [sample-subscriptions] :as file-monitor} sample-file-name channel]
  (swap! sample-subscriptions update-in [sample-file-name] #(vec (remove (partial = channel) %))))

(defrecord FileMonitor [sample-subscriptions sample-watcher-close]
  Lifecycle
  (start [this]
    (let [sample-subscriptions (atom {})]
      (assoc this
        :sample-subscriptions sample-subscriptions
        :sample-watcher-close (start-watch [{:path (-> "./src/sample"
                                                       (File.)
                                                       .getCanonicalFile)
                                             :event-types [:modify]
                                             :callback (partial sample-notify sample-subscriptions)
                                             :options {:recursive true}}]))))
  (stop [this]
    ((:sample-watcher-close this))
    (assoc this
      :sample-subscriptions nil
      :sample-watcher-close nil)))
