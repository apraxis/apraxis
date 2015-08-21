(ns apraxis.service
  (:require [clojure.string :as str]
            [io.pedestal.http :as server]
            [io.pedestal.interceptor :refer [defbefore]]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [figwheel-sidecar.auto-builder :as figwheel-auto]
            [figwheel-sidecar.core :as figwheel]
            [clojurescript-build.auto :as cljs-auto]
            [apraxis.service.dev :as dev]
            [apraxis.service.file-monitor :as filemon]
            [apraxis.service.middleman :as middleman]
            [com.stuartsierra.component :as component :refer (Lifecycle start stop)]))


(defn to-ns
  [sym-or-ns]
  (if (symbol? sym-or-ns)
    (the-ns sym-or-ns)
    sym-or-ns))

(defonce ^:private apraxis-service (atom nil))

(defrecord Apraxis
  [svc-fn target-ns dev-service service app-name]
  Lifecycle
  (start [this]
    (let [app-str (str/replace (str app-name) \- \_)
          service (-> (svc-fn)
                      (merge {:env :dev
                              ;; do not block thread that starts web server
                              ::server/join? false
                              ;; Routes can be a function that resolve routes,
                              ;;  we can use this to set the routes to be reloadable
                              ::server/routes (fn [] ((ns-resolve target-ns 'routes)))
                              ;; all origins are allowed in dev mode
                              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
                      ;; Wire up interceptor chains
                      server/default-interceptors
                      server/dev-interceptors
                      (dev/apraxis-dev-interceptors dev-service)
                      server/create-server
                      server/start)
          figwheel-server (figwheel/start-server {:css-dirs ["resources/public/css"]})
          autobuild-config {:builds [{:id app-str
                                      :source-paths [(format "src/client/components/%s" app-str) "../apraxis/src/cljs"]
                                      :build-options {:output-to (format "target/apraxis-js/js/%s_client.js" app-str)
                                                      :output-dir "target/apraxis-js/js/out"
                                                      :optimizations :none
                                                      :pretty-print true
                                                      :preamble ["react/react.js"]
                                                      :externs ["react/externs/react.js"]}}]
                            :figwheel-server figwheel-server
                            }
          fig-autobuilder (figwheel-auto/autobuild* autobuild-config)]

      (-> this
          (assoc :service service)
          (assoc :autobuilder fig-autobuilder))))
  (stop [this]
    (-> this
        (update-in [:autobuilder] cljs-auto/stop-autobuild!)
        (update-in [:figwheel-server] (comp (constantly nil) figwheel/stop-server))
        (update-in [:service] server/stop))))

(defn run-apraxis
  [app-name]
  (let [target-ns (-> app-name
                      (str ".service")
                      symbol
                      to-ns)
        svc-fn (ns-resolve target-ns 'service)
        service (component/system-map
                  :middleman (middleman/map->Middleman {:target-dir "target"})
                  :file-monitor (component/using (filemon/map->FileMonitor {})
                                                 [:middleman])
                  :dev-service (component/using (dev/map->DevService {:app-name app-name})
                                                [:file-monitor])
                  :apraxis (component/using (map->Apraxis {:svc-fn svc-fn
                                                           :target-ns target-ns
                                                           :app-name app-name})
                                            [:dev-service]))]

    (reset! apraxis-service {:system (component/start service)
                             :app-name app-name})))

(defn stop-apraxis
  []
  (when-not @apraxis-service
    (throw (ex-info "No apraxis service running, cannot stop."
                    {:current-apraxis-service apraxis-service})))
  (swap! apraxis-service update-in [:system] component/stop))

(defn restart-apraxis
  []
  (when-not @apraxis-service
    (throw (ex-info "No apraxis service running, cannot restart."
                    {:current-apraxis-service apraxis-service})))
  (stop-apraxis)
  (-> @apraxis-service :app-name run-apraxis))
