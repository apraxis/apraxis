(ns apraxis.util.jruby
  (:require [clojure.java.io :as io])
  (:import (org.jruby.embed ScriptingContainer LocalContextScope)
           (java.io FileReader File)
           (java.nio.file Files)
           java.nio.file.attribute.FileAttribute
           (java.util HashMap)))

(def ^:dynamic *target-root* nil)

(def target-resources
  {"config.rb" "/middleman/config.rb"
   "Gemfile" "/middleman/Gemfile"
   "dotruby-version" "/middleman/.ruby-version"
   "vendor_src/component_template.html.haml" "/middleman/vendor_src/component_template.html.haml"})

(defn copy-resource
  [target-dir resource-path target-path]
  (io/copy (io/reader (io/resource resource-path)) (File. (str target-dir target-path))))

(defn anchored-target-resources
  [target-dir]
  (mapcat
   (fn [[resource target-path]]
     [(str target-dir target-path)
      #(copy-resource target-dir resource target-path)])
   target-resources))

(defn create-src-symlink
  [target-dir]
  (let [expected (File. (str target-dir "/middleman/source"))]
    (when-not (.exists expected)
      (Files/createSymbolicLink (.toPath expected)
                                (.toPath (.getCanonicalFile (File. "src")))
                                (make-array FileAttribute 0)))))

(defn ensure-files
  [file-fns-vec]
  (doseq [[file creation-fn] (partition 2 file-fns-vec)]
    (when-not (.exists (File. file))
      (creation-fn))))

(defn ensure-middleman-env
  [target-dir]
  (let [dir-reqs (mapcat (fn [filename]
                           [filename #(.mkdir (File. filename))])
                         [target-dir (str target-dir "/middleman") (str target-dir "/jruby")
                          (str target-dir "/middleman/vendor_src")])
        src-symlink-req [(str target-dir "/middleman/src") #(create-src-symlink target-dir)]]
    (ensure-files (concat dir-reqs src-symlink-req (anchored-target-resources target-dir)))))

(defmacro with-target-root
  [target-root-name & body]
  `(do (ensure-middleman-env ~target-root-name)
       (binding [*target-root* ~target-root-name]
         ~@body)))

(defn fresh-scripting-container
  ([sc-root] (fresh-scripting-container sc-root {}))
  ([sc-root env]
   (let [sc (ScriptingContainer. LocalContextScope/THREADSAFE)
         sc-root (.getCanonicalPath (File. sc-root))
         base-env (.getEnvironment sc)]
     (.setCurrentDirectory sc sc-root)
     (.setEnvironment sc (merge (into {} base-env)
                                {"GEM_HOME" (str *target-root* "/jruby")
                                 "JRUBY_OPTS" "--2.0"}
                                env))
     sc)))

(defn run-ruby-resource-in-container
  [container resource]
  (let [stream (io/reader (io/resource resource))
        path (.getFile (io/resource resource))]
    (.runScriptlet container stream path)))

(defn update-rubygems
  []
  (let [sc (fresh-scripting-container (str *target-root* "/middleman"))]
    (run-ruby-resource-in-container sc "ensure_latest_rubygems.rb")))

(defn ensure-bundler
  []
  (let [sc (fresh-scripting-container (str *target-root* "/middleman"))]
    (run-ruby-resource-in-container sc "ensure_bundler.rb")))

(defn ensure-middleman
  []
  (let [sc (fresh-scripting-container (str *target-root* "/middleman"))]
    (.put sc "vendor_path" "vendor/bundle")
    (run-ruby-resource-in-container sc "ensure_middleman.rb")))

(defn run-middleman-build
  []
  (let [mm-root (str *target-root* "/middleman")
        canonical-mm-root (.getCanonicalPath (File. mm-root ))
        sc (fresh-scripting-container mm-root
                                      {"MM_ROOT" canonical-mm-root})]
    (run-ruby-resource-in-container sc "middleman_build.rb")))

(defn make-middleman-adapter
  []
  (let [mm-root (str *target-root* "/middleman")
        canonical-mm-root (.getCanonicalPath (File. mm-root))
        sc (fresh-scripting-container mm-root
                                      {"MM_ROOT" canonical-mm-root})]
    [(.newObjectAdapter sc) (run-ruby-resource-in-container sc "middleman_adapter.rb")]))
