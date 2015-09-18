(ns apraxis.client.template
  (:require [fs.core :as fs]
            [kioo.om :as kom]
            [clojure.java.io :as io]
            [clojure-watch.core :refer [start-watch]])
  (:import java.io.File))

(defn- add-dependency [the-ns-path dep-path]
  (let [dep-canonical-path (-> dep-path
                               io/resource
                               .getFile)
        closer (promise)
        watcher (start-watch [{:path (-> dep-canonical-path
                                         File.
                                         .getParent)
                               :event-types [:modify :create]
                               :callback (fn [event filename]
                                           (when (= filename dep-canonical-path)
                                             (fs/touch the-ns-path)
                                             (@closer)))
                               :options {:recursive false}}])]
    (deliver closer watcher)))

(defmacro defsnippet
  ([sym path sel args] (do (add-dependency cljs.analyzer/*cljs-file* path)
                           `(kom/defsnippet ~sym ~path ~sel ~args)))
  ([sym path sel args trans] (do (add-dependency cljs.analyzer/*cljs-file* path)
                                 `(kom/defsnippet ~sym ~path ~sel ~args ~trans)))
  ([sym path sel args trans opts] (do (add-dependency cljs.analyzer/*cljs-file* path)
                                      `(kom/defsnippet ~sym ~path ~sel ~args ~trans ~opts))))

(defmacro deftemplate
  ([sym path args] (do (add-dependency cljs.analyzer/*cljs-file* path)
                       `(kom/deftemplate ~sym ~path ~args)))
  ([sym path args trans] (do (add-dependency cljs.analyzer/*cljs-file* path)
                             `(kom/deftemplate ~sym ~path ~args ~trans)))
  ([sym path args trans opts] (do (add-dependency cljs.analyzer/*cljs-file* path)
                                  `(kom/deftemplate ~sym ~path ~args ~trans ~opts))))
