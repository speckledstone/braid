(ns braid.client.mobile.views
  (:require
    [cljs.core.async :as a]
    [braid.client.gateway.views :refer [gateway-view]]
    [braid.client.helpers :refer [->color]]
    [braid.client.mobile.auth-flow.views :refer [auth-flow-view]]
    [braid.client.mobile.style :refer [styles]]
    [braid.client.routes :as routes]
    [braid.client.ui.views.header :refer [group-name-view group-header-buttons-view]]
    [braid.client.ui.views.new-message :refer [upload-button-view]]
    [braid.client.ui.views.sidebar]
    [braid.client.ui.views.thread :refer [messages-view]]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [retouch.core :refer [drawer-view swipe-view]])
  (:import
    [goog.events KeyCodes]))

(defn new-message-view [config]
  (let [message (r/atom "")
        group-id (subscribe [:open-group-id])]
    (fn [config]
      [:div.new.message
       [upload-button-view {:thread-id (config :thread-id)
                            :group-id @group-id}]
       [:div.autocomplete-wrapper
        [:textarea {:value @message
                    :placeholder (config :placeholder)
                    :on-change
                    (fn [e]
                      (reset! message (.. e -target -value)))
                    :on-key-up
                    (fn [e]
                      (condp = (.-keyCode e)
                        KeyCodes.ENTER
                        (do
                          (dispatch [:new-message {:group-id @group-id
                                                   :thread-id (config :thread-id)
                                                   :mentioned-tag-ids (config :mentioned-tag-ids)
                                                   :mentioned-user-ids (config :mentioned-user-ids)
                                                   :content @message}])

                          (reset! message ""))
                        nil))}]]])))

(defn thread-view [thread]
  (let [open? (subscribe [:thread-open? (thread :id)])]
    (fn [thread]
      [:div.thread
       [:div.card
        [:div.head
         [braid.client.ui.views.thread/thread-tags-view thread]
         (when @open?
           [:div.close {:on-click (fn [_]
                                    (dispatch [:hide-thread {:thread-id (thread :id)}]))}])]
        [messages-view thread]
        [new-message-view {:thread-id (thread :id)
                           :placeholder (if (thread :new?)
                                          "Start a conversation..."
                                          "Reply...")
                           :mentioned-user-ids (when (thread :new?)
                                                 (thread :mentioned-ids))
                           :mentioned-tag-ids (when (thread :new?)
                                                (thread :tag-ids))}]]])))

(defn header-view [toggle-draw-ch]
  (let [group-id (subscribe [:open-group-id])]
    (fn [_]
      [:div.group-header {:style {:background-color (->color @group-id)}}
       [:div.bar
        [group-name-view]
        [:span.buttons
         [:a.open-sidebar {:on-click (fn [] (a/put! toggle-draw-ch true))}]]
        [:span.spacer]
        [group-header-buttons-view [{:title "Inbox"
                                     :route-fn routes/inbox-page-path
                                     :class "inbox"}
                                    {:title "Settings"
                                     :route-fn routes/group-settings-path
                                     :class "settings"}]]]])))

(defn inbox-view [toggle-draw-ch]
  (let [group-id (subscribe [:open-group-id])
        threads (subscribe [:open-threads] [group-id])
        temp-thread (subscribe [:temp-thread])]
    (fn [_]
      [:div.inbox.page
       [header-view toggle-draw-ch]
       [:div.threads
        [swipe-view (conj @threads
                          @temp-thread) thread-view]]])))

(defn main-view []
  (let [toggle-draw-ch (a/chan)]
    (fn []
      [:div.main
       [drawer-view
        toggle-draw-ch
        [:div.sidebar
         [braid.client.ui.views.sidebar/groups-view]]]
       [inbox-view toggle-draw-ch]])))

(defn style-view []
  [:style {:type "text/css"
           :dangerouslySetInnerHTML {:__html styles}}])

(defn app-view []
  (let [login-state (subscribe [:login-state])]
    (fn []
      [:div.app
       [style-view]
       (case @login-state
         :gateway
         [gateway-view]

         :auth-check
         [:div.status.authenticating "Authenticating..."]

         :ws-connect
         [:div.status.connecting "Connecting..."]

         :login-form
         [auth-flow-view]

         :app
         [main-view]

         :init
         [:div.status])])))
