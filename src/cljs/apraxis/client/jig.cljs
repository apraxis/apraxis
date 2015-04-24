(ns apraxis.client.jig
  (:require [om.core :as om]
            [om.dom :as dom]
            [pixels-against-humanity.test]))

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
  []
  (let [app-state (atom {:component pixels-against-humanity.test/test-component
                         :data [{} "go" :us]})]
    (om/root jig-component app-state
             {:target (.getElementById js/document "jig-root")})))
