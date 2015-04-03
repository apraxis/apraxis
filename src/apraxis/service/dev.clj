(ns apraxis.service.dev
  (:require [io.pedestal.interceptor :refer [defbefore]]
            [io.pedestal.http.route :refer [router]]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.log :as log]
            [io.pedestal.impl.interceptor :as pincept]
            [ring.util.response :as response]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io])
  (:import (java.io File)
           (clojure.lang RT)))

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
  (let [component (-> request :path-params :component)
        component-resource (io/resource (str "structure/components/" component "/index.html"))
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

(defroutes dev-routes
  [[["/components/*component" {:get component-renderer}]]])

(def dev-router
  (router dev-routes))

(defbefore dev-service
  [{request :request :as context}]
  (let [dev-path-info (str "/" (-> request :path-params :dev-route))
        dev-context (-> context
                        (assoc-in [:request :path-info] dev-path-info)
                        (update-in [::pincept/queue] conj dev-router))]
    dev-context))
