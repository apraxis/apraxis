(ns apraxis.client.jig
  (:require [om.core :as om]
            [om.dom :as dom]
            [pixels-against-humanity.test]
            [cljs.reader :as reader]))

(defn jig-component
  [{:keys [component data]} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
             (mapcat (fn [d]
                       [(dom/div nil (str "data: " (pr-str d)))
                        (om/build component d)
                        (dom/hr nil)])
                     data)))))

(defn ^:export -main
  [js-obj]
  (let [app-state (atom {:component (js/eval (.-component js-obj))
                         :api-root (aget js-obj "api-root")
                         :component-name (aget js-obj "component-name")
                         :data '()})
        stream-url (str (:api-root @app-state)
                        "/sample-streams/"
                        (:component-name @app-state))
        source (js/EventSource. stream-url)]
    (.addEventListener source
                       "sample-set"
                       (fn [e]
                         (let [vals (reader/read-string (.-data e))]
                           (swap! app-state assoc :data vals))))
    (om/root jig-component app-state
             {:target (.getElementById js/document "jig-root")})))
