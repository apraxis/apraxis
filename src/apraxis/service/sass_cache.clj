(ns apraxis.service.sass-cache
  (require [clojure.core.async :as async]
           [apraxis.service.middleman :as middleman]
           [io.pedestal.interceptor :refer (interceptor)]
           [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
           [figwheel-sidecar.components.figwheel-server :as fig]))

;; Consumes:
;; fs events for style
;; notification services from figwheel
;; sass calculation from middleman

;; provides:
;; css caching interceptor

;; manages:
;; cache of css response bodies.
;; async sub to style pub

(defn css-request?
  [request]
  (-> request
      :path-info
      (.endsWith ".css")))

(defn css-response-future
  [figwheel middleman path]
  (future
    (let [response (middleman/raw-response middleman path)
          figwheel-server (-> figwheel
                              :figwheel-system
                              :figwheel-system
                              :system
                              deref
                              :figwheel-server)]
      (fig/send-message figwheel-server
                        ::fig/broadcast
                        { :msg-name :css-files-changed
                         :files [path]})
      response)))

(defn add-css
  [cached-css figwheel middleman path]
  (assoc cached-css path (css-response-future figwheel middleman path)))


(defn css-interceptor-enter-fn
  [figwheel middleman css-cache {:keys [request response] :as context}]
  (if (css-request? request)
    (let [path (:path-info request)
          future (get @css-cache path)]
      (cond (nil? future) (async/thread (assoc context :response (-> css-cache
                                                                     (swap! add-css figwheel middleman path)
                                                                     (get path)
                                                                     deref)))
            (realized? future) (assoc context :response @future)
            true (async/thread (assoc context :response @future))))
    context))

(defn make-css-interceptor
  [figwheel middleman css-cache]
  (interceptor {:enter (partial css-interceptor-enter-fn figwheel middleman css-cache)
                :name ::sass-cache}))

(defn recalc-all-css
  [cached-css figwheel middleman]
  (->> (for [[path response-future] cached-css]
         (let [new-response-future (css-response-future figwheel middleman path)]
           [path new-response-future]))
       (into {})))

(defrecord SassCache [file-monitor figwheel middleman css-interceptor css-cache style-sub]
  Lifecycle
  (start [this]
    (let [style-sub (async/chan (async/sliding-buffer 5))
          css-cache (atom {})
          css-interceptor (make-css-interceptor figwheel middleman css-cache)]
      (async/sub (:file-event-pub file-monitor) :style style-sub)
      (async/go-loop [event (async/<! style-sub)]
        (when event
          (swap! css-cache recalc-all-css figwheel middleman)
          (recur (async/<! style-sub))))
      (assoc this
        :css-cache css-cache
        :style-sub style-sub
        :css-interceptor css-interceptor)))
  (stop [this]
    (-> this
        (assoc :css-cache nil
               :css-interceptor nil)
        (update :style-sub async/close!))))
