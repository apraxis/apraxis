(ns apraxis.service.file-monitor
  (:require [clojure.core.async :as async]
            [clojure-watch.core :refer [start-watch]]
            [io.pedestal.log :as log]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)])
  (:import (java.io File)))


(defrecord FileMonitor [file-event-sink file-event-pub sample-close structure-close style-close]
  Lifecycle
  (start [this]
    (let [file-event-sink (async/chan)
          file-event-pub (async/pub file-event-sink :type)]
      (assoc this
        :file-event-sink file-event-sink
        :file-event-pub file-event-pub
        :sample-close (start-watch [{:path (-> "./src/sample"
                                               (File.)
                                               .getCanonicalFile)
                                     :event-types [:modify :create :delete]
                                     :callback (fn [event filename]
                                                 (async/put! file-event-sink {:type :sample
                                                                              :event event
                                                                              :filename filename}))
                                     :options {:recursive true}}])
        :structure-close (start-watch [{:path (-> "./src/structure"
                                                  (File.)
                                                  .getCanonicalFile)
                                        :event-types [:modify :create :delete]
                                        :callback (fn [event filename]
                                                    (async/put! file-event-sink {:type :structure
                                                                                 :event event
                                                                                 :filename filename}))
                                        :options {:recursive true}}])
        :style-close (start-watch [{:path (-> "./src/style"
                                              (File.)
                                              .getCanonicalFile)
                                    :event-types [:modify :create :delete]
                                    :callback (fn [event filename]
                                                (async/put! file-event-sink {:type :style
                                                                             :event event
                                                                             :filename filename}))
                                    :options {:recursive true}}]))))
  (stop [this]
    (try ((:sample-close this))
         (catch Exception e
           (log/error e)))
    (try ((:structure-close this))
         (catch Exception e
           (log/error e)))
    (try ((:style-close this))
         (catch Exception e
           (log/error e)))
    (try (async/close! file-event-sink)
         (catch Exception e
           (log/error e)))
    (assoc this
      :file-event-sink nil
      :file-event-pub nil
      :sample-close nil
      :haml-close nil
      :sass-close nil)))
