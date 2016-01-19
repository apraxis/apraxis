(ns apraxis.service.figwheel
  (:require [apraxis.client.template :as template]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [figwheel-sidecar.system :as figwheel-system]
            [figwheel-sidecar.components.cljs-autobuild :as figwheel-build]
            [figwheel-sidecar.build-middleware.injection :as injection]
            [figwheel-sidecar.build-middleware.notifications :as notifications]
            [figwheel-sidecar.build-middleware.clj-reloading :as clj-reloading]
            [figwheel-sidecar.build-middleware.javascript-reloading :as javascript-reloading]
            [clojurescript-build.auto :as cljs-auto]
            [clojure.string :as str]))

(defn strify
  [app-name]
  (str/replace (str app-name) \- \_))

(defn template-provider-hook
  [build-fn middleman]
  (fn [build-state]
    (binding [template/*html-resource-provider* middleman]
      (build-fn build-state))))

(defn cljs-build-fn
  [middleman]
  (-> figwheel-build/cljs-build
      (template-provider-hook middleman)
      notifications/hook
      clj-reloading/hook
      javascript-reloading/hook
      figwheel-build/figwheel-start-and-end-messages))

(defrecord Figwheel
    [app-name middleman figwheel-system]
  Lifecycle
  (start [this]
    (let [app-str (strify app-name)
          options {:figwheel-options {:cljs-build-fn (cljs-build-fn middleman)}
                   :build-ids [app-str]
                   :all-builds [{:id app-str
                                 :figwheel {:websocket-host :js-client-host
                                            :on-jsload "apraxis.client.jig/om-reset"}
                                 :source-paths [(format "src/client/components/%s" app-str) "../apraxis/src/cljs"]
                                 :compiler {:output-to (format "target/apraxis-js/js/%s_client.js" app-str)
                                            :output-dir "target/apraxis-js/js/out"
                                            :optimizations :none
                                            :pretty-print true
                                            }}]}

          figwheel-system (figwheel-system/create-figwheel-system options)]
      (assoc this
        :figwheel-system (component/start figwheel-system))))
  (stop [this]
    (-> this
        (update :figwheel-system component/stop))))
