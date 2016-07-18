(ns app.core
  (:require [cljs.nodejs :as node]
            [app.tweets :as tweets]
            [app.action :as action]
            [app.message :as message]
            [app.specs :as specs]
            [cljs.spec :as spec]
            [cljs.core.async :refer [<! put! close! chan >!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(node/enable-util-print!)

(defn handle-error [reason payload cb]
  (let [error (clj->js {:type :error
                        :error reason
                        :payload payload})]
    (println (.stringify js/JSON error))
    (cb error nil)))

(defn ^:export handler [event context cb]
  (if-let [{:keys [payload type]} (action/convert event)]
    (go
      (let [twitter-data (<! (tweets/fetch (second payload)))]
        (when (:has-more? twitter-data)
          (let [user-action  (action/create (select-keys twitter-data [:user :min-id]))]
            (<! (message/send user-action :user))))
        (when (:has-tweets? twitter-data)
          (let [tweet-action (action/create (:tweets twitter-data))]
            (<! (message/send tweet-action :created_at))))
        (cb nil (clj->js "succes"))))
    (cb "Invalid Event" nil)))

(defn -main [] identity)
(set! *main-cli-fn* -main)
