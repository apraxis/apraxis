(ns apraxis.service.dev-component
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [cheshire.core :refer [generate-string]]
            [apraxis.client.template :as template]
            [net.cgrand.enlive-html :as html :refer [defsnippet template]]
            [clojure.string :as str]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)])
  (:import (java.io File)
           (clojure.lang LineNumberingPushbackReader)))

(defprotocol DevComponent
  (sample-data [this] "Return the sample data for this component.")
  (html-body [this scheme server-name server-port] "Return the HTML body to view this component.")
  (cljs-source [this] "Return path to the cljs source file for this component"))

(defn sample-file-name
  [component-name]
  (format "./src/sample/%s.edn" (munge component-name)))

(defn js-name
  [& cljs-names]
  (str/join  "." (map namespace-munge cljs-names)))

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

(defrecord FilesystemDevComponent [app-name component-name]
  DevComponent
  (sample-data [this]
    (let [sample-file-name (sample-file-name component-name)]
      (if (-> sample-file-name
              File.
              .exists)
        (with-open [reader (-> sample-file-name
                               io/reader
                               LineNumberingPushbackReader.)]
          (doall (take-while (partial not= ::end)
                             (repeatedly #(edn/read {:eof ::end} reader)))))
        '({}))))
  (html-body [this scheme server-name server-port]
    (let [component-fn (format "%s_component" (munge component-name))
          cljs-resource (io/file (io/resource (format "%s/%s.cljs" (munge app-name) (munge component-name))))]
      (if cljs-resource
        (str/join (jig-template app-name component-name component-fn scheme server-name server-port))
        (template/resolve-component-structure (munge component-name)))))
  (cljs-source [this]
    (.getCanonicalPath (io/file (io/resource (format "%s/%s.cljs" (munge app-name) (munge component-name)))))))

(defn dev-component
  [app-name component-name]
  (FilesystemDevComponent. app-name component-name))

(defn sample-file-name->component-name
  [sample-file-name]
  (second (re-matches #".*/src/sample/(.*)\.edn" sample-file-name)))

(defn structure-file-name->component-name
  [structure-file-name]
  (second (re-matches #".*/src/structure/components/_(.*)\.haml" structure-file-name)))

(defrecord DevComponentPusher [app-name file-monitor component-event-sink component-event-pub sample-sub structure-sub]
  Lifecycle
  (start [this]
    (let [component-event-sink (async/chan)
          component-event-pub (async/pub component-event-sink :type)
          sample-sub (async/chan (async/sliding-buffer 5))
          structure-sub (async/chan (async/sliding-buffer 5))]
      (async/sub (:file-event-pub file-monitor) :sample sample-sub)
      (async/sub (:file-event-pub file-monitor) :structure structure-sub)
      (async/go-loop [event (async/<! sample-sub)]
        (let [component-name (sample-file-name->component-name (:filename event))]
          (async/>! component-event-sink {:type :sample
                                          :component (dev-component app-name component-name)})
          (recur (async/<! sample-sub))))
      (async/go-loop [event (async/<! structure-sub)]
        (let [component-name (structure-file-name->component-name (:filename event))]
          (when component-name
            (async/>! component-event-sink {:type :structure
                                            :component (dev-component app-name component-name)}))
          (recur (async/<! structure-sub))))
      (assoc this :component-event-sink component-event-sink
             :component-event-pub component-event-pub
             :sample-sub sample-sub
             :structure-sub structure-sub)))
  (stop [this]
    (-> this
        (update :component-event-sink async/close!)
        (update :sample-sub async/close!)
        (update :structure-sub async/close!)
        (dissoc :component-event-pub))))
