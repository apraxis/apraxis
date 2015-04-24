(ns apraxis.service.dev
  (:require [io.pedestal.interceptor :refer [defbefore]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :refer [router]]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.log :as log]
            [io.pedestal.impl.interceptor :as pincept]
            [ring.util.response :as response]
            [ring.middleware.resource :as ring-resource]
            [ring.middleware.content-type :as ring-content-type]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io])
  (:import (java.io File)
           (clojure.lang RT)))

(defn expose-app-name
  [app-name]
  (pincept/interceptor :enter (fn [context] (assoc context ::app-name app-name))
                       :name ::expose-app-name))

(defn ambient-app-name
  [service-map app-name]
  (update-in service-map [::bootstrap/interceptors]
             #(-> app-name
                  expose-app-name
                  (cons %)
                  vec)))

(def middleman-build-classpath-hack
  (delay
   (let [target-middleman (File. "./target/middleman/build")]
     (RT/addURL (.toURL (.toURI (.getCanonicalFile target-middleman)))))))

(defbefore dev-index
  [context]
  (assoc context
         :response
         {:body "Apraxis Development Tools"
          :headers {}
          :status 200}))

(defbefore component-renderer
  [{:keys [request] :as context}]
  @middleman-build-classpath-hack
  (let [app-name (::app-name context)
        component (-> request :path-params :component)
        component-resource (io/resource "templates/component_jig.html")
        #_(io/resource (str "structure/components/" component "/index.html"))
        ;; TODO: rewrite CSS and other URLS "/" -> "/dev/static/"
        response (if component-resource
                   (-> component-resource
                       io/file
                       response/response
                       (response/content-type "text/html"))
                   {:body (str "No component '" component "' found. Searching in " (io/resource (str "structure/components")))
                    :headers {}
                    :status 200})]
    (assoc context
           :response response)))

(defbefore static-renderer
  [{:keys [request] :as context}]
  @middleman-build-classpath-hack
  (let [resource-path-info (str "/" (-> request :path-params :resource))
        request (assoc request :path-info resource-path-info)
        response (-> request
                     (ring-resource/resource-request "")
                     (ring-content-type/content-type-response request))]
    (assoc context :response response)))

(defroutes dev-routes
  [[["/components/*component" {:get component-renderer}]
    ["/static/*resource" {:get static-renderer}]]])

(def dev-router
  (router dev-routes))

(defbefore dev-service
  [{request :request :as context}]
  (let [dev-path-info (str "/" (-> request :path-params :dev-route))
        dev-context (-> context
                        (assoc-in [:request :path-info] dev-path-info)
                        (update-in [::pincept/queue] conj dev-router))]
    dev-context))
