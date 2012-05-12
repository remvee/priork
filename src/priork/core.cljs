(ns priork.core
  (:require [goog.string :as s]))

(defn map->js
  "Transform CLJ map into JS map."
  [m]
  (let [out (js-obj)]
    (doseq [[k v] m] (aset out (name k) v))
    out))

;; setup jquery
(def $ (js* "$"))

;; serve first focussable element
($ #(-> ($ ".focus") (.focus)))

;; setup sortable
($ #(-> ($ ".tasks")
        (.sortable (map->js
                    {:update (fn []
                               (.post $
                                      "order"
                                      (map->js
                                       {:ids (-> ($ (js* "this"))
                                                 (.sortable "toArray"))})))}))))

;; setup inplace editing
(defn update-handler [event]
  (.preventDefault event)
  (let [li (aget (.parent ($ (js* "this"))) 0)
        id (.getAttribute li "id")
        text (s/unescapeEntities (. (js* "this") -innerHTML))]
    (set! (. li -innerHTML)
          (str "<form class='update' action='update' method='post'><div>"
               "<input type='text' autocomplete='off' name='task'>"
               "<input type='hidden' name='id' value='" id "'>"
               "</div></form>"))
    (-> ($ li) (.find "input[type='text']") (.focus))
    (-> ($ li) (.find "input[type='text']") (.val text))
    (-> ($ li)
        (.find "form.update")
        (.submit (fn [event]
                   (.preventDefault event)
                   (.post $
                          "update"
                          (-> ($ (js* "this")) (.serialize))
                          (fn [text]
                            (let [parent (. li -parentNode)
                                  c (.createElement js/document "div")
                                  _ (set! (. c -innerHTML) text)
                                  n (aget (. c -children) 0)]
                              (. parent (replaceChild n li))
                              (-> ($ n) (.find "a.update") (.click update-handler))))))))))
($ #(-> ($ "li.task a.update") (.click update-handler)))
