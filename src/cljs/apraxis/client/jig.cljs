(ns apraxis.client.jig
  (:require [om.core :as om]
            [om.dom :as dom]
            [figwheel.client :as figwheel-client]
            [pixels-against-humanity.test]
            [cljs.reader :as reader]
            [goog.string.format]
            [goog.string :as gstring]))

(defn jig-component
  [{:keys [component data]} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
             (mapcat (fn [d]
                       [(dom/div #js {:className "data-text"} (pr-str d))
                        (om/build component d)
                        (dom/hr nil)])
                     data)))))

(defn ^:export -main
  [js-obj]
  (let [app-state (atom {:component (js/eval (.-component js-obj))
                         :api-root (aget js-obj "api-root")
                         :component-name (aget js-obj "component-name")
                         :data '()})
        host (aget js-obj "host")
        stream-url (str (:api-root @app-state)
                        "/sample-streams/"
                        (:component-name @app-state))
        source (js/EventSource. stream-url)
        root (.getElementById js/document "jig-root")]
    (.addEventListener source
                       "sample-set"
                       (fn [e]
                         (let [vals (reader/read-string (.-data e))]
                           (swap! app-state assoc :data vals))))
    (figwheel-client/watch-and-reload
     :websocket-url   (gstring/format "ws://%s:3449/figwheel-ws" host)
     :jsload-callback (fn []
                        (om/detach-root root)
                        (om/root jig-component app-state {:target root})))
    (om/root jig-component app-state
             {:target root})))
