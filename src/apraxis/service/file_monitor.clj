(ns apraxis.service.file-monitor
  (:require [apraxis.service.middleman :as middleman]
            [clojure.core.async :refer [put! chan]]
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

(defn middleman-build
  [middleman event filename]
  (middleman/build middleman))

(defrecord FileMonitor [sample-subscriptions sample-watcher-close middleman haml-close sass-close]
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
                                             :options {:recursive true}}])
        :haml-close (start-watch [{:path (-> "./src/structure"
                                             (File.)
                                             .getCanonicalFile)
                                   :event-types [:modify :create]
                                   :callback (partial middleman-build middleman)
                                   :options {:recursive true}}])
        :sass-close (start-watch [{:path (-> "./src/style"
                                             (File.)
                                             .getCanonicalFile)
                                   :event-types [:modify :create]
                                   :callback (partial middleman-build middleman)
                                   :options {:recursive true}}]))))
  (stop [this]
    (try ((:sample-watcher-close this))
         (catch Exception e
           (log/error e)))
    (try ((:haml-close this))
         (catch Exception e
           (log/error e)))
    (try ((:sass-close this))
         (catch Exception e
           (log/error e)))
    (assoc this
      :sample-subscriptions nil
      :sample-watcher-close nil
      :haml-close nil
      :sass-close nil)))
