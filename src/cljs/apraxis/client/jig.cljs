(ns apraxis.client.jig
  (:require [om.core :as om]
            [om.dom :as dom]
            [cljs.reader :as reader]
            [goog.string.format]
            [goog.string :as gstring]
            [figwheel.client :as figwheel]))

(defn jig-component
  [{:keys [component data]} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
             (mapcat (fn [d]
                       [(dom/div #js {:className "data-text"} (pr-str d))
                        (om/build (js/eval component) d)
                        (dom/hr nil)])
                     data)))))

(defonce *root* (atom nil))

(defonce *app-state* (atom {}))

(defn om-reset
  []
  (om/detach-root @*root*)
  (om/root jig-component *app-state* {:target @*root*}))

(defn initialize-app-state
  [state js-obj]
  (-> state
      (assoc :component (.-component js-obj))
      (assoc :api-root (aget js-obj "api-root"))
      (assoc :component-name (aget js-obj "component-name"))
      (assoc :application-name (aget js-obj "application-name"))
      (assoc :data '())))

(defn ^:export -main
  [js-obj]
  (let [root (reset! *root* (.getElementById js/document "jig-root"))
        app-state (swap! *app-state* initialize-app-state js-obj)
        host (aget js-obj "host")
        stream-url (str (:api-root @*app-state*)
                        "/sample-streams/"
                        (:component-name @*app-state*))
        source (js/EventSource. stream-url)]
    (figwheel/start  {:on-jsload om-reset
                      :websocket-url (gstring/format "ws://%s:3449/figwheel-ws" host)
                      :build-id (:application-name app-state)})
    (.addEventListener source
                       "sample-set"
                       (fn [e]
                         (let [vals (reader/read-string (.-data e))]
                           (swap! *app-state* assoc :data vals))))
    (om/root jig-component *app-state* {:target root})))
