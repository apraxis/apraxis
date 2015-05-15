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
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html :refer [deftemplate]])
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

(deftemplate jig-template
  "templates/component_jig.html"
  [app-name component component-fn]
  [:#component-require] (html/content (str "goog.require('"
                                           (namespace-munge app-name)
                                           "."
                                           (namespace-munge component)
                                           "');"))
  [:#component-invoke] (html/content (str "apraxis.client.jig._main("
                                          (namespace-munge app-name)
                                          "."
                                          (namespace-munge component)
                                          "."
                                          (namespace-munge component-fn)
                                          ");")))

(defbefore component-renderer
  [{:keys [request] :as context}]
  @middleman-build-classpath-hack
  (let [app-name (::app-name context)
        component (-> request :path-params :component)
        component-fn (str component "_component")
        ;; component-resource (io/resource (str "structure/components/" component "/index.html"))
        ;; response-body (io/file component-resource)
        ;; TODO: rewrite CSS and other URLS "/" -> "/dev/static/"
        response-body (apply str (jig-template app-name component component-fn))
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
