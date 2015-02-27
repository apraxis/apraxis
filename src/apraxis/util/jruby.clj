(ns apraxis.util.jruby
  (:require [clojure.java.io :as io])
  (:import (org.jruby.embed ScriptingContainer LocalContextScope)
           (java.io FileReader File)
           (java.nio.file Files)
           java.nio.file.attribute.FileAttribute
           (java.util HashMap)))

(def jgem-resource-path
  "META-INF/jruby.home/bin/jgem")

(defn ensure-bundler-stream
  []
  (io/reader (io/resource "ensure_bundler.rb")))

(defn ensure-bundler-path
  []
  (.getFile (io/resource "ensure_bundler.rb")))

(defn ensure-bundler
  [target-dir]
  (let [sc (ScriptingContainer. LocalContextScope/THREADSAFE)]
    (.setCurrentDirectory sc (.getCanonicalPath (File. (str target-dir "/middleman"))))
    (.runScriptlet sc (ensure-bundler-stream) (ensure-bundler-path))))

(defn ensure-middleman-stream
  []
  (io/reader (io/resource "ensure_middleman.rb")))

(defn ensure-middleman-path
  []
  (.getFile (io/resource "ensure_middleman.rb")))

(defn ensure-middleman
  [target-dir]
  (let [sc (ScriptingContainer. LocalContextScope/THREADSAFE)]
    (.setCurrentDirectory sc (.getCanonicalPath (File. (str target-dir "/middleman"))))
    (.put sc "vendor_path" "vendor/bundle")
    (.runScriptlet sc (ensure-middleman-stream) (ensure-middleman-path))))

(defn ensure-middleman-env
  [target-dir]
  (doseq [dir [target-dir (str target-dir "/middleman")]]
    (.mkdir (File. dir)))
  (io/copy (io/reader (io/resource "config.rb")) (File. (str target-dir "/middleman/config.rb")))
  (io/copy (io/reader (io/resource "Gemfile")) (File. (str target-dir "/middleman/Gemfile")))
  (Files/createSymbolicLink (.toPath (File. (str target-dir "/middleman/src")))
                            (.toPath (.getCanonicalFile (File. "src")))
                            (make-array FileAttribute 0)))

(defn run-middleman-stream
  []
  (io/reader (io/resource "middleman_build.rb")))

(defn run-middleman-path
  []
  (.getFile (io/resource "middleman_build.rb")))

(defn run-middleman-build
  [target-dir]
  (let [sc (ScriptingContainer. LocalContextScope/THREADSAFE)
        base-env (.getEnvironment sc)
        canonical-mm-root (.getCanonicalPath (File. (str target-dir "/middleman")))]
    (.setEnvironment sc (doto (HashMap.) (.putAll (merge (into {} base-env) {"MM_ROOT" canonical-mm-root}))))
    (.setCurrentDirectory sc canonical-mm-root)
    (.runScriptlet sc (run-middleman-stream) (run-middleman-path))))
