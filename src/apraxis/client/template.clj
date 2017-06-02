(ns apraxis.client.template
  (:require [kioo.om :as kom]
            [clojure.java.io :as io]
            [clojure.string :as str]
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

(defmacro keep-when
  "Returns the matching nodes (unmodified) when 
  the given predicate holds."
  [pred]
  `(when ~pred identity))

(defmacro remove-when
  "Removes the matching nodes when the given 
  predicate holds."
  [pred]
  `(when-not ~pred identity))

(defmacro defsnippet
  ([sym path sel args]
     `(kom/defsnippet ~sym (resolve-component-structure ~path) ~sel ~args))
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

(defmacro defroottemplate
  [sym args & body]
  (let [resource-name (-> *ns*
                          ns-name
                          name
                          (str/split #"\.")
                          last)
        any (symbol "any")
        transforms (apply hash-map body)]
    `(defsnippet ~sym
       ~resource-name
       [:#component-root :> ~any]
       ~args
       ~transforms
       {:resource-wrapper :mini-html})))
