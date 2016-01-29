(ns apraxis.client.jig
  (:require [om.core :as om]
            [om.dom :as dom]
            [cljs.reader :as reader]
            [goog.string.format]
            [goog.string :as gstring]
            [figwheel.client :as figwheel]))

(defn jig-example
  [datum owner]
  (reify
    om/IRenderState
    (render-state
        [_ {:keys [component] :as state}]
        (dom/div nil
               (dom/div #js {:className "data-text"} (pr-str datum))
               (om/build component datum)
               (dom/hr nil)))))

(defn jig-component
  [{:keys [component data] :as props} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
             (om/build-all jig-example data {:init-state {:component (js/eval component)}})))))

(defn ^:export -main
  [js-obj]
  (let [root (.getElementById js/document "jig-root")
        app-state (atom {:data []
                         :application-name (aget js-obj "application-name")
                         :component-name (aget js-obj "component-name")
                         :api-root (aget js-obj "api-root")
                         :component (.-component js-obj)})
        host (aget js-obj "host")
        stream-url (str (:api-root @app-state)
                        "/sample-streams/"
                        (:component-name @app-state))
        source (js/EventSource. stream-url)]
    (figwheel/start  {:on-jsload (fn []
                                   (om/detach-root root)
                                   (om/root jig-component app-state {:target root}))
                      :websocket-url (gstring/format "ws://%s:3449/figwheel-ws" host)
                      :build-id (:application-name app-state)})
    (.addEventListener source
                       "sample-set"
                       (fn [e]
                         (let [vals (vec (reader/read-string (.-data e)))]
                           (swap! app-state assoc :data vals))))
    (om/root jig-component app-state {:target root})))
