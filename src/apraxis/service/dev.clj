(ns apraxis.service.dev
  (:require [io.pedestal.interceptor :refer [defbefore definterceptorfn]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :refer [router]]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.log :as log]
            [io.pedestal.impl.interceptor :as pincept]
            [io.pedestal.http.sse :as sse]
            [ring.util.response :as response]
            [ring.middleware.resource :as ring-resource]
            [ring.middleware.content-type :as ring-content-type]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [net.cgrand.enlive-html :as html :refer [deftemplate]]
            [clojure.core.async :refer [go put! >! <! chan mult tap untap close!]]
            [cheshire.core :refer [generate-string]]
            [clojure-watch.core :refer [start-watch]]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [apraxis.service.file-monitor :as filemon])
  (:import (java.io File)
           (clojure.lang RT LineNumberingPushbackReader)))

(defbefore dev-index
  [context]
  (assoc context
         :response
         {:body "Apraxis Development Tools"
          :headers {}
          :status 200}))

(defn js-name
  [& cljs-names]
  (apply str (interpose "." (map namespace-munge cljs-names))))

(defn sample-file-name
  [component]
  (str "./src/sample/" component ".edn"))

(defn sample-data
  [component]
  (let [reader (-> (sample-file-name component)
                   io/reader
                   LineNumberingPushbackReader.)]
    (take-while (partial not= ::end)
                (repeatedly #(edn/read {:eof ::end} reader)))))

(defn sample-streamer
  [file-monitor event-ch {:keys [request response] :as context}]
  (let [component (-> (:path-params request) :component)
        sample-file-name (-> component
                             sample-file-name
                             (File.)
                             .getCanonicalPath)
        feed (filemon/sample-subscribe file-monitor sample-file-name)]
    (go
      (>! event-ch {:name "sample-set"
                    :data (pr-str (sample-data component))})
      (loop [[event filename] (<! feed)]
        (let [write (try (>! event-ch {:name "sample-set"
                                       :data (pr-str (sample-data component))})
                         true
                         (catch Throwable t
                           (filemon/sample-unsubscribe file-monitor sample-file-name feed)
                           (close! feed)
                           false))]
          (when write (recur (<! feed))))))))

(defn main-js-obj
  [app-name component component-fn scheme server-name server-port]
  (generate-string {"component" (js-name app-name component component-fn)
                    "component-name" component
                    "api-root" (str scheme "://" server-name ":" server-port "/dev")
                    "host" server-name}))

(deftemplate jig-template
  "templates/component_jig.html"
  [app-name component component-fn scheme server-name server-port]
  [:#component-require] (html/content (str "goog.require('"
                                           (js-name app-name component)
                                           "');"))
  [:#component-invoke] (html/content (str "apraxis.client.jig._main("
                                          (main-js-obj app-name
                                                       component
                                                       component-fn
                                                       scheme
                                                       server-name
                                                       server-port)
                                          ");")))

(defbefore component-renderer
  [{:keys [request] :as context}]
  (let [app-name (::app-name context)
        component (-> request :path-params :component)
        component-fn (str component "_component")
        scheme (name (get-in context [:request :headers "x-forwarded-proto"]
                             (-> context :request :scheme)))
        server-name (-> context :request :server-name)
        server-port (get (-> context :request :headers) "x-forwarded-port"
                         (-> context :request :server-port))
        ;; component-resource (io/resource (str "structure/components/" component "/index.html"))
        ;; response-body (io/file component-resource)
        ;; TODO: rewrite CSS and other URLS "/" -> "/dev/static/"
        response-body (apply str (jig-template app-name component component-fn scheme server-name server-port))
        response (if response-body
                   (-> response-body
                       response/response
                       (response/content-type "text/html"))
                   {:body (str "No component '" component "' found. Searching in " (io/resource (str "structure/components")))
                    :headers {}
                    :status 200})]
    (assoc context
      :response response)))

(defbefore static-renderer
  [{:keys [request] :as context}]
  (let [resource-path-info (str "/" (-> request :path-params :resource))
        request (assoc request :path-info resource-path-info)
        response (-> request
                     (ring-resource/resource-request "")
                     (ring-content-type/content-type-response request))]
    (assoc context :response response)))

(defn dev-routes
  [file-monitor]
  (expand-routes
   `[[["/dev" {:get dev-index}
       ["/components/*component" {:get component-renderer}]
       ["/static/*resource" {:get static-renderer}]
       ["/sample-streams/*component" {:get [::sample-streamer ~(sse/start-event-stream (partial sample-streamer file-monitor))]}]]]]))

(defn expose-app-name
  [app-name]
  (pincept/interceptor :enter (fn [context] (assoc context ::app-name app-name))
                       :name ::expose-app-name))

(defn dev-pre-route
  [file-monitor]
  (let [dev-router (router (dev-routes file-monitor))]
    (pincept/interceptor :enter (fn [context]
                                  (let [new-ctx ((:enter dev-router) context)]
                                    (if (nil? (:route new-ctx))
                                      context
                                      new-ctx)))
                         :name ::dev-pre-route)))

(defrecord DevService
    [file-monitor app-name dev-interceptors]
  Lifecycle
  (start [this]
    (let [target-middleman (File. "./target/middleman/build")]
      (RT/addURL (.toURL (.toURI (.getCanonicalFile target-middleman)))))
    (assoc this :dev-interceptors [(expose-app-name app-name) (dev-pre-route file-monitor)]))
  (stop [this]
    (assoc this :dev-interceptors nil)))

(defn apraxis-dev-interceptors
  [service-map dev-service]
  (update-in service-map [::bootstrap/interceptors]
             #(vec (concat % (:dev-interceptors dev-service)))))
