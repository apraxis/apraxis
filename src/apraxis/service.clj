(ns apraxis.service
  (:require [io.pedestal.http :as server]
            [io.pedestal.interceptor :refer [defbefore]]
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
    [svc-fn target-ns dev-service service]
  Lifecycle
  (start [this]
    (let [service (-> (svc-fn)
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
                      server/start)]
      (assoc this :service service)))
  (stop [this]
    (update-in this [:service] server/stop)))

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
                                                          :target-ns target-ns})
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
