(ns app.core
  (:require [cljs.nodejs :as node]
            [app.tweets :as tweets]
            [app.event :as event]
            [app.bookmark :as bookmark]
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
  (println (.stringify js/JSON (clj->js event)))
  (let [event (event/convert event)]
    (if (spec/valid? ::specs/event event)
      (go
        (let [{:keys [payload type]} (spec/conform ::specs/event event)
              twitter-data           (<! (tweets/fetch (second payload)))]
          (when-let [user-message (message/create :user-data twitter-data)]
            (println (.stringify js/JSON (clj->js user-message)))
            (println (<! (message/send user-message))))
          (when-let [bookmarks-message (message/create :bookmarks twitter-data)]
            (println (<! (message/send bookmarks-message)))
            (println (.stringify js/JSON (clj->js bookmarks-message))))
          (cb nil (clj->js "completed"))))
      (handle-error :specs-not-matched (spec/explain-data ::specs/event event) cb))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
