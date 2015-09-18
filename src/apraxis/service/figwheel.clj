(ns apraxis.service.figwheel
  (:require [com.stuartsierra.component :as component :refer (Lifecycle start stop)]
            [figwheel-sidecar.auto-builder :as figwheel-auto]
            [figwheel-sidecar.core :as figwheel]
            [clojurescript-build.auto :as cljs-auto]
            [clojure.string :as str]))

(defn strify
  [app-name]
  (str/replace (str app-name) \- \_))

(defrecord Figwheel
    [app-name figwheel-server autobuilder]
  Lifecycle
  (start [this]
    (let [app-str (strify app-name)
          figwheel-server (figwheel/start-server {:css-dirs ["target/middleman/build/style"]})
          autobuild-config {:builds [{:id app-str
                                      :source-paths [(format "src/client/components/%s" app-str) "../apraxis/src/cljs"]
                                      :build-options {:output-to (format "target/apraxis-js/js/%s_client.js" app-str)
                                                      :output-dir "target/apraxis-js/js/out"
                                                      :optimizations :none
                                                      :pretty-print true
                                                      :preamble ["react/react.js"]
                                                      :externs ["react/externs/react.js"]}}]
                            :figwheel-server figwheel-server}
          fig-autobuilder (figwheel-auto/autobuild* autobuild-config)]
      (assoc this
        :autobuilder fig-autobuilder
        :figwheel-server figwheel-server)))
  (stop [this]
    (-> this
        (update-in [:autobuilder] cljs-auto/stop-autobuild!)
        (update-in [:figwheel-server] (comp (constantly nil) figwheel/stop-server)))))
