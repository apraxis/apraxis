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
                         :data (try (reader/read-string (.-data js-obj))
                                    (catch js/Error e
                                      (.log js/console e)
                                      [{}]))})]
    (om/root jig-component app-state
             {:target (.getElementById js/document "jig-root")})))
