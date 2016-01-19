(ns apraxis.service.dev
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.core.async :refer [go put! >! <! chan mult tap untap close!]]
            [clojure-watch.core :refer [start-watch]]
            [cheshire.core :refer [generate-string]]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [io.pedestal.interceptor :refer [defbefore definterceptorfn]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :refer [router]]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.log :as log]
            [io.pedestal.impl.interceptor :as pincept]
            [io.pedestal.http.sse :as sse]
            [ring.util.response :as response]
            [ring.middleware.resource :as ring-resource]
            [ring.middleware.content-type :as ring-content-type]
            [net.cgrand.enlive-html :as html :refer [defsnippet template]]
            [apraxis.service.file-monitor :as filemon]
            [apraxis.service.middleman :as middleman]
            [apraxis.client.template :as template])
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
  (str/join  "." (map namespace-munge cljs-names)))

(defn sample-file-name
  [component]
  (str "./src/sample/" component ".edn"))

(defn sample-data
  [component]
  (let [sample-file-name (sample-file-name component)]
    (if (-> sample-file-name
            (File.)
            .exists)
      (with-open [reader (-> sample-file-name
                             io/reader
                             LineNumberingPushbackReader.)]
        (doall (take-while (partial not= ::end)
                           (repeatedly #(edn/read {:eof ::end} reader)))))
      '({}))))

(defn sample-streamer
  [file-monitor event-ch {:keys [request response] :as context}]
  (let [component (-> request :path-params :component)
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
  (generate-string {"application-name" (munge app-name)
                    "component" (js-name app-name component component-fn)
                    "component-name" component
                    "api-root" (str scheme "://" server-name ":" server-port "/dev")
                    "host" server-name}))

(defsnippet jig-body "templates/component_jig.html" [:#jig-body]
  [app-name component component-fn scheme server-name server-port]
  [:#component-bootstrap] (html/set-attr :src (str "/js/" (namespace-munge app-name) "_client.js"))
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

(defn jig-template
  [app-name component component-fn scheme server-name server-port]
  (let [base-template (template (template/resolve-component-structure (munge component))
                                [app-name component component-fn scheme server-name server-port]
                                [:#component-root] (html/content (jig-body app-name component component-fn scheme server-name server-port)))]
    (base-template app-name component component-fn scheme server-name server-port)))

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
        cljs-resource (io/file (io/resource (format "%s/%s.cljs" (munge app-name) (munge component))))
        response-body (if cljs-resource
                        (str/join (jig-template app-name component component-fn scheme server-name server-port))
                        (template/resolve-component-structure (munge component)))
        response (if response-body
                   (-> response-body
                       response/response
                       (response/content-type "text/html"))
                   {:body (str "No component '" component "' found. Searching in " (io/resource (str "build/structure/components")))
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

(defn embedded-middleman-html-resource-provider
  [middleman]
  (pincept/interceptor :enter (fn [context]
                                (assoc-in context [:bindings #'template/*html-resource-provider*] middleman))
                       :name ::mm-html-resource-provider))

(defn ruby-symbol->clj-keyword
  [ruby-symbol]
  (keyword (.asJavaString ruby-symbol)))

(defn middleman-last-resort
  [middleman]
  (pincept/interceptor :leave (fn [context]
                                (if (-> context :response some?)
                                  context
                                  (let [path (-> context :request :path-info)
                                        result (->> path
                                                    (middleman/raw-response middleman)
                                                    (map (fn [[k v]] [(ruby-symbol->clj-keyword k) v]))
                                                    (into {}))]
                                    (if (= 200 (:status result))
                                      (assoc context :response result)
                                      context))))
                       :name ::mm-last-resort-handler))

(defrecord DevService
    [file-monitor middleman app-name dev-interceptors]
  Lifecycle
  (start [this]
    (let [target-middleman (File. "./target/middleman/build")]
      (RT/addURL (.toURL (.toURI (.getCanonicalFile target-middleman)))))
    (assoc this :dev-interceptors [(embedded-middleman-html-resource-provider middleman)
                                   (expose-app-name app-name)
                                   (middleman-last-resort middleman)
                                   (dev-pre-route file-monitor)]))
  (stop [this]
    (assoc this :dev-interceptors nil)))

(defn apraxis-dev-interceptors
  [service-map dev-service]
  (update-in service-map [::bootstrap/interceptors]
             #(vec (concat % (:dev-interceptors dev-service)))))
