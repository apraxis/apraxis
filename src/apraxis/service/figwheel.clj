(ns apraxis.service.figwheel
  (:require [apraxis.client.template :as template]
            [apraxis.service.dev-component :as dev-component]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [figwheel-sidecar.system :as figwheel-system]
            [figwheel-sidecar.components.cljs-autobuild :as figwheel-build]
            [figwheel-sidecar.build-middleware.injection :as injection]
            [figwheel-sidecar.build-middleware.notifications :as notifications]
            [figwheel-sidecar.build-middleware.clj-reloading :as clj-reloading]
            [figwheel-sidecar.build-middleware.javascript-reloading :as javascript-reloading]
            [figwheel-sidecar.utils :as futils]
            [clojurescript-build.auto :as cljs-auto]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [cljs.build.api :as bapi]))

(defn strify
  [app-name]
  (str/replace (str app-name) \- \_))

(defn template-provider-hook
  [build-fn middleman]
  (fn [build-state]
    (binding [template/*html-resource-provider* middleman]
      (build-fn build-state))))

(defn cljs-autobuild-trapping-hook
  [build-fn autobuilder-promise]
  (fn [build-state]
    (when-not (realized? autobuilder-promise)
      (deliver autobuilder-promise (dissoc build-state :changed-files)))
    (build-fn build-state)))

(defn cljs-build-fn
  [middleman autobuilder-promise]
  (-> figwheel-build/cljs-build
      (template-provider-hook middleman)
      notifications/hook
      clj-reloading/hook
      javascript-reloading/hook
      figwheel-build/figwheel-start-and-end-messages
      (cljs-autobuild-trapping-hook autobuilder-promise)))

(defn subscribe-to-structure
  [channel dev-component-pusher]
  (async/sub (:component-event-pub dev-component-pusher) :structure channel))

(defn clunk-figwheel-build
  [{:keys [figwheel-server build-config] :as cljs-autobuild} component]
  (let [log-writer (or (:log-writer cljs-autobuild)
                       (:log-writer figwheel-server)
                       (io/writer "figwheel_server.log" :append true))
        cljs-build-fn (or (:cljs-build-fn cljs-autobuild)
                          (:cljs-build-fn figwheel-server))
        src-file (dev-component/cljs-source component)
        output-file (bapi/src-file->target-file src-file (:build-options build-config))]
    (.delete output-file)
    (futils/sync-exec
     (fn []
       (binding [*out* log-writer
                 *err* log-writer]
         (cljs-build-fn
          (assoc cljs-autobuild
            :changed-files [(dev-component/cljs-source component)])))))))

(defrecord Figwheel
    [app-name middleman dev-component-pusher figwheel-system structure-sub]
  Lifecycle
  (start [this]
    (let [app-str (strify app-name)
          autobuilder-promise (promise)
          options {:figwheel-options {:cljs-build-fn (cljs-build-fn middleman autobuilder-promise)}
                   :build-ids [app-str]
                   :all-builds [{:id app-str
                                 :figwheel {:websocket-host :js-client-host
                                            :on-jsload "apraxis.client.jig/om-reset"}
                                 :source-paths [(format "src/client/components/%s" app-str) "../apraxis/src/cljs"]
                                 :compiler {:output-to (format "target/apraxis-js/js/%s_client.js" app-str)
                                            :output-dir "target/apraxis-js/js/out"
                                            :optimizations :none
                                            :pretty-print true}}]}
          structure-sub (doto (async/chan (async/sliding-buffer 5))
                          (subscribe-to-structure dev-component-pusher))
          figwheel-system (figwheel-system/create-figwheel-system options)]
      (async/go-loop [{:keys [component] :as event} (async/<! structure-sub)]
        (clunk-figwheel-build @autobuilder-promise component)
        (recur (async/<! structure-sub)))
      (assoc this
        :figwheel-system (component/start figwheel-system)
        :structure-sub structure-sub)))
  (stop [this]
    (-> this
        (update :figwheel-system component/stop)
        (update :structure-sub async/close!))))
