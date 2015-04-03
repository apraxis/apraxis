(ns apraxis.service
  (:require [io.pedestal.http :as server]
            [io.pedestal.interceptor :refer [defbefore]]
            [apraxis.service.dev :as dev]))

(def dev-index dev/dev-index)

(def dev-service dev/dev-service)

(defn to-ns
  [sym-or-ns]
  (if (symbol? sym-or-ns)
    (the-ns sym-or-ns)
    sym-or-ns))

(defonce ^:private apraxis-service (atom nil))

(defn run-apraxis
  [service-ns]
  (let [target-ns (to-ns service-ns)
        svc-fn (ns-resolve target-ns 'service)]
    (reset! apraxis-service {:service-ns service-ns
                             :service (-> (svc-fn) ;; start with production configuration
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
                                          server/create-server
                                          server/start)})))

(defn stop-apraxis
  []
  (when-not @apraxis-service
    (throw (ex-info "No apraxis service running, cannot stop."
                    {:current-apraxis-service apraxis-service})))
  (swap! apraxis-service #(update-in % [:service] server/stop)))

(defn restart-apraxis
  []
  (when-not @apraxis-service
    (throw (ex-info "No apraxis service running, cannot restart."
                    {:current-apraxis-service apraxis-service})))
  (stop-apraxis)
  (-> @apraxis-service :service-ns run-apraxis))
