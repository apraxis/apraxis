(ns apraxis.service.dev
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [io.pedestal.interceptor.helpers :refer [defbefore]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :refer [router]]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor :as pincept]
            [io.pedestal.http.sse :as sse]
            [ring.util.response :as response]
            [ring.middleware.resource :as ring-resource]
            [ring.middleware.content-type :as ring-content-type]
            [apraxis.service.dev-component :as dev-component]
            [apraxis.service.middleman :as middleman]
            [apraxis.client.template :as template])
  (:import (java.io File)
           (clojure.lang RT LineNumberingPushbackReader)
           (java.lang Throwable)))

(defbefore dev-index
  [context]
  (assoc context
         :response
         {:body "Apraxis Development Tools"
          :headers {}
          :status 200}))

(defn component-sample-event?
  [app-name component-name component]
  (= [app-name component-name] ((juxt :app-name :component-name) component)))

(defn sse-event-for-component
  [component]
  {:name "sample-set"
   :data (pr-str (dev-component/sample-data component))})

(defn sample-streamer
  [component-pusher event-ch {:keys [request response] :as context}]
  (let [app-name (::app-name context)
        component-name (-> request :path-params :component)
        component (dev-component/dev-component app-name component-name)
        feed (async/chan (async/sliding-buffer 5) (comp (map :component)
                                            (filter (partial component-sample-event? app-name component-name))
                                            (map sse-event-for-component)))]
    (async/sub (:component-event-pub component-pusher) :sample feed)
    (async/>!! event-ch (sse-event-for-component component))
    (async/pipe feed event-ch)))

(defbefore component-renderer
  [{:keys [request] :as context}]
  (let [app-name (::app-name context)
        component-name (-> request :path-params :component)
        component (dev-component/dev-component app-name component-name)

        scheme (name (get-in context [:request :headers "x-forwarded-proto"]
                             (-> context :request :scheme)))
        server-name (-> context :request :server-name)
        server-port (get (-> context :request :headers) "x-forwarded-port"
                         (-> context :request :server-port))
        response-body (dev-component/html-body component scheme server-name server-port)
        response (if response-body
                   (-> response-body
                       response/response
                       (response/content-type "text/html"))
                   {:body (str "No component '" component-name "' found.")
                    :headers {}
                    :status 404})]
    (assoc context
           :response response)))

(defn dev-routes
  [dev-component-pusher]
  (expand-routes
   `[[["/dev" {:get dev-index}
       ["/components/*component" {:get component-renderer}]
       ["/sample-streams/*component" {:get [::sample-streamer ~(sse/start-event-stream (partial sample-streamer dev-component-pusher))]}]]]]))

(defn expose-app-name
  [app-name]
  (pincept/interceptor {:enter (fn [context] (assoc context ::app-name app-name))
                        :name ::expose-app-name}))

(defn dev-pre-route
  [dev-component-pusher]
  (let [dev-router (router (dev-routes dev-component-pusher))]
    (pincept/interceptor {:enter (fn [context]
                                   (let [new-ctx ((:enter dev-router) context)]
                                     (if (nil? (:route new-ctx))
                                       context
                                       new-ctx)))
                          :name ::dev-pre-route})))

(defn embedded-middleman-html-resource-provider
  [middleman]
  (pincept/interceptor {:enter (fn [context]
                                 (assoc-in context [:bindings #'template/*html-resource-provider*] middleman))
                        :name ::mm-html-resource-provider}))

(defn middleman-last-resort
  [middleman]
  (pincept/interceptor {:leave (fn [context]
                                 (if (-> context :response some?)
                                   context
                                   (let [path (-> context :request :path-info) 
                                         result (middleman/raw-response middleman path)]
                                     (if (= 200 (:status result))
                                       (assoc context :response result)
                                       context))))
                        :name ::mm-last-resort-handler}))

(defrecord DevService
    [dev-component-pusher sass-cache middleman app-name dev-interceptors]
  Lifecycle
  (start [this]
    (assoc this :dev-interceptors [(embedded-middleman-html-resource-provider middleman)
                                   (expose-app-name app-name)
                                   (middleman-last-resort middleman)
                                   (:css-interceptor sass-cache)
                                   (dev-pre-route dev-component-pusher)]))
  (stop [this]
    (assoc this :dev-interceptors nil)))

(defn apraxis-dev-interceptors
  [service-map dev-service]
  (update-in service-map [::bootstrap/interceptors]
             #(vec (concat % (:dev-interceptors dev-service)))))
