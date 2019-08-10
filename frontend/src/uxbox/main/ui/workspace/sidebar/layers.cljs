;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2015-2016 Juan de la Cruz <delacruzgarciajuan@gmail.com>
;; Copyright (c) 2015-2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.main.ui.workspace.sidebar.layers
  (:require
   [lentes.core :as l]
   [rumext.alpha :as mf]
   [uxbox.builtins.icons :as i]
   [uxbox.main.data.pages :as udp]
   [uxbox.main.data.shapes :as uds]
   [uxbox.main.data.workspace :as udw]
   [uxbox.main.store :as st]
   [uxbox.main.ui.keyboard :as kbd]
   [uxbox.main.ui.shapes.icon :as icon]
   [uxbox.main.ui.workspace.sortable :refer [use-sortable]]
   [uxbox.util.data :refer [classnames]]
   [uxbox.util.dom :as dom]
   [uxbox.util.i18n :refer (tr)]))

;; --- Helpers

(defn- element-icon
  [item]
  (case (:type item)
    :icon (icon/icon-svg item)
    :image i/image
    :line i/line
    :circle i/circle
    :path i/curve
    :rect i/box
    :text i/text
    :group i/folder
    nil))

;; --- Layer Name

(mf/defc layer-name
  [{:keys [shape] :as props}]
  (let [local (mf/use-state {})
        on-blur (fn [event]
                  (let [target (dom/event->target event)
                        parent (.-parentNode target)
                        name (dom/get-value target)]
                    (set! (.-draggable parent) true)
                    (st/emit! (uds/rename-shape (:id shape) name))
                    (swap! local assoc :edition false)))
        on-key-down (fn [event]
                      (js/console.log event)
                      (when (kbd/enter? event)
                        (on-blur event)))
        on-click (fn [event]
                   (dom/prevent-default event)
                   (let [parent (.-parentNode (.-target event))]
                     (set! (.-draggable parent) false))
                   (swap! local assoc :edition true))]
    (if (:edition @local)
      [:input.element-name
       {:type "text"
        :on-blur on-blur
        :on-key-down on-key-down
        :auto-focus true
        :default-value (:name shape "")}]
      [:span.element-name
       {:on-double-click on-click}
       (:name shape "")])))

;; --- Layer Item

(mf/defc layer-item
  [{:keys [shape selected index] :as props}]
  (letfn [(toggle-blocking [event]
            (dom/stop-propagation event)
            (let [id (:id shape)
                  blocked? (:blocked shape)]
              (if blocked?
                (st/emit! (uds/unblock-shape id))
                (st/emit! (uds/block-shape id)))))

          (toggle-visibility [event]
            (dom/stop-propagation event)
            (let [id (:id shape)
                  hidden? (:hidden shape)]
              (if hidden?
                (st/emit! (uds/show-shape id))
                (st/emit! (uds/hide-shape id)))
              (when (contains? selected id)
                (st/emit! (udw/select-shape id)))))

          (select-shape [event]
            (dom/prevent-default event)
            (let [id (:id shape)]
              (cond
                (or (:blocked shape)
                    (:hidden shape))
                nil

                (.-ctrlKey event)
                (st/emit! (udw/select-shape id))

                (> (count selected) 1)
                (st/emit! (udw/deselect-all)
                          (udw/select-shape id))

                (contains? selected id)
                (st/emit! (udw/select-shape id))

                :else
                (st/emit! (udw/deselect-all)
                          (udw/select-shape id)))))

          (on-drop [item monitor]
            (st/emit! (udp/persist-page (:page shape))))

          (on-hover [item monitor]
            (st/emit! (udw/change-shape-order {:id (:shape-id item)
                                               :index index})))]
    (let [selected? (contains? selected (:id shape))
          [dprops dnd-ref] (use-sortable
                            {:type "layer-item"
                             :data {:shape-id (:id shape)
                                    :page-id (:page shape)
                                    :index index}
                             :on-hover on-hover
                             :on-drop on-drop})]
      [:li {:ref dnd-ref
            :class (classnames
                    :selected selected?
                    :dragging-TODO (:dragging? dprops))}
       [:div.element-list-body {:class (classnames :selected selected?)
                                :on-click select-shape
                                :on-double-click #(dom/stop-propagation %)
                                :draggable true}
        [:div.element-actions
         [:div.toggle-element {:class (when-not (:hidden shape) "selected")
                               :on-click toggle-visibility}
          i/eye]
         [:div.block-element {:class (when (:blocked shape) "selected")
                              :on-click toggle-blocking}
          i/lock]]
        [:div.element-icon (element-icon shape)]
        [:& layer-name {:shape shape}]]])))

;; --- Layers List

(def ^:private shapes-iref
  (-> (l/key :shapes)
      (l/derive st/state)))

(mf/defc layers-list
  [{:keys [shapes selected] :as props}]
  (let [shapes-map (mf/deref shapes-iref)]
    [:div.tool-window-content
     [:ul.element-list
      (for [[index id] (map-indexed vector shapes)]
        [:& layer-item {:shape (get shapes-map id)
                        :selected selected
                        :index index
                        :key id}])]]))

;; --- Layers Toolbox

(mf/defc layers-toolbox
  [{:keys [page selected] :as props}]
  (let [on-click #(st/emit! (udw/toggle-flag :layers))]
    [:div#layers.tool-window
     [:div.tool-window-bar
      [:div.tool-window-icon i/layers]
      [:span (tr "ds.layers")]
      [:div.tool-window-close {:on-click on-click} i/close]]
     [:& layers-list {:shapes (:shapes page)
                      :selected selected}]]))
