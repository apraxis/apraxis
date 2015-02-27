(ns apraxis.util.jruby
  (:require [clojure.java.io :as io])
  (:import (org.jruby.embed ScriptingContainer LocalContextScope)
           (java.io FileReader File)
           (java.nio.file Files)
           java.nio.file.attribute.FileAttribute
           (java.util HashMap)))

(def jgem-resource-path
  "META-INF/jruby.home/bin/jgem")

(def ^:dynamic *target-root* nil)

(defn copy-config
  [target-dir]
  (io/copy (io/reader (io/resource "config.rb")) (File. (str target-dir "/middleman/config.rb"))))

(defn copy-gemfile
  [target-dir]
  (io/copy (io/reader (io/resource "Gemfile")) (File. (str target-dir "/middleman/Gemfile"))))

(defn create-src-symlink
  [target-dir]
  (Files/createSymbolicLink (.toPath (File. (str target-dir "/middleman/src")))
                            (.toPath (.getCanonicalFile (File. "src")))
                            (make-array FileAttribute 0)))

(defn ensure-files
  [file-fns-vec]
  (doseq [[file creation-fn] (partition 2 file-fns-vec)]
    (when-not (.exists (File. file))
      (creation-fn))))

(defn ensure-middleman-env
  [target-dir]
  (let [dir-reqs (mapcat (fn [filename]
                           [filename #(.mkdir (File. filename))])
                         [target-dir (str target-dir "/middleman") (str target-dir "/jruby")])
        config-req [(str target-dir "/middleman/config.rb") #(copy-config target-dir)]
        gemfile-req [(str target-dir "/middleman/Gemfile") #(copy-gemfile target-dir)]
        src-symlink-req [(str target-dir "/middleman/src") #(create-src-symlink target-dir)]]
    (ensure-files (concat dir-reqs config-req gemfile-req src-symlink-req))))

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
                                {"GEM_HOME" (str *target-root* "/jruby")}
                                env))
     sc)))

(defn ensure-bundler-stream
  []
  (io/reader (io/resource "ensure_bundler.rb")))

(defn ensure-bundler-path
  []
  (.getFile (io/resource "ensure_bundler.rb")))

(defn ensure-bundler
  []
  (let [sc (fresh-scripting-container (str *target-root* "/middleman"))]
    (.runScriptlet sc (ensure-bundler-stream) (ensure-bundler-path))))

(defn ensure-middleman-stream
  []
  (io/reader (io/resource "ensure_middleman.rb")))

(defn ensure-middleman-path
  []
  (.getFile (io/resource "ensure_middleman.rb")))

(defn ensure-middleman
  []
  (let [sc (fresh-scripting-container (str *target-root* "/middleman"))]
    (.put sc "vendor_path" "vendor/bundle")
    (.runScriptlet sc (ensure-middleman-stream) (ensure-middleman-path))))

(defn run-middleman-stream
  []
  (io/reader (io/resource "middleman_build.rb")))

(defn run-middleman-path
  []
  (.getFile (io/resource "middleman_build.rb")))

(defn run-middleman-build
  []
  (let [mm-root (str *target-root* "/middleman")
        canonical-mm-root (.getCanonicalPath (File. mm-root ))
        sc (fresh-scripting-container mm-root
                                      {"MM_ROOT" canonical-mm-root})]
    (.runScriptlet sc (run-middleman-stream) (run-middleman-path))))
