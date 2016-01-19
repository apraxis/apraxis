(ns apraxis.client.template
  (:require [kioo.om :as kom]
            [clojure.java.io :as io]
            [clojure-watch.core :refer [start-watch]])
  (:import java.io.File))

(defprotocol HtmlResourceProvider
  (html-stream [this component-name] "Returns a byte stream containing the template HTML structure for component-name"))

(deftype ClasspathHtmlResourceProvider []
  HtmlResourceProvider
  (html-stream [_ component-name]
    (.openStream (io/resource (format "build/structure/components/%s/index.html" component-name)))))

(def ^:dynamic *html-resource-provider* (ClasspathHtmlResourceProvider.))

(defn resolve-component-structure
  [path]
  (html-stream *html-resource-provider* path))

(defmacro defsnippet
  ([sym path sel args]
     `(kom/defsnippet ~sym (resolve-component-structure ~path) stream ~sel ~args))
  ([sym path sel args trans]
     `(kom/defsnippet ~sym (resolve-component-structure ~path) ~sel ~args ~trans))
  ([sym path sel args trans opts]
     `(kom/defsnippet ~sym (resolve-component-structure ~path) ~sel ~args ~trans ~opts)))

(defmacro deftemplate
  ([sym path args]
     `(kom/deftemplate ~sym (resolve-component-structure ~path) ~args))
  ([sym path args trans]
     `(kom/deftemplate ~sym (resolve-component-structure ~path) ~args ~trans))
  ([sym path args trans opts]
     `(kom/deftemplate ~sym (resolve-component-structure ~path) ~args ~trans ~opts)))
