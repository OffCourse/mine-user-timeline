(ns app.core
  (:require [cljs.nodejs :as node]
            [app.twitter :as twitter]
            [app.event :as event]
            [app.specs :as specs]
            [cljs.spec :as spec]
            [cljs.core.match :refer-macros [match]]
            [cljs.core.async :refer [<! put! close! chan >!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private AWS (node/require "aws-sdk"))
(node/enable-util-print!)
(def Kinesis (new AWS.Kinesis))

(defn create-tweets-message [tweets]
  {:StreamName "tweeted-bookmarks"
   :Records (->> tweets
                 (map (fn [tweet]
                        {:Data (.stringify js/JSON (clj->js tweet))
                         :PartitionKey "user"})))})

(defn create-user-message [user-name max-id]
  {:StreamName "requested-twitter-users"
   :Records [(let [data {:user user-name
                        :max-id max-id}]
              {:Data (.stringify js/JSON (clj->js data))
               :PartitionKey "user"})]})

(defn send-message [msg]
  (let [c (chan)]
    (.putRecords Kinesis (clj->js msg) #(if %1
                                          (println "error" %1)
                                          (go (>! c %2))))
    c))

(defn handle-error [reason payload cb]
  (let [error (clj->js {:type :error
                        :error reason
                        :payload payload})]
    (println (.stringify js/JSON error))
    (cb error nil)))

(defn extract-bookmark [tweet]
  {:user (-> tweet :user :screen_name)
   :url (-> tweet :entities :urls first :expanded_url)
   :timestamp (-> tweet :created_at)})

(defn extract-bookmarks [tweets]
  (->> tweets
       (map extract-bookmark)
       (filter #(:url %1))))

(defn process-event [event cb]
  (if (spec/valid? ::specs/event event)
    (go
      (let [{:keys [payload type]} (spec/conform ::specs/event event)
            [_ items]              payload
            {:keys [user max-id]}  (first items)
            tweets                 (<! (twitter/get-tweets user max-id))
            bookmarks              (spec/conform ::specs/bookmarks (extract-bookmarks tweets))
            min-id                 (or (apply min (map :id tweets)) 0)
            tweets-message         (create-tweets-message bookmarks)
            tweets-response        (<! (send-message tweets-message))
            user-message           (create-user-message user min-id)]
        (when (or (not max-id) (> max-id min-id))
          (println (.stringify js/JSON (clj->js user-message)))
          #_(<! (send-message user-message)))
        (println (.stringify js/JSON (clj->js tweets-message)))
        (cb nil (clj->js tweets-response))))
    (handle-error :specs-not-matched (spec/explain-data ::specs/event event) cb)))

(defn ^:export handler [event context cb]
  (println (.stringify js/JSON (clj->js event)))
  (let [event (event/convert event)]
    (process-event event cb)))

(defn -main [] identity)
(set! *main-cli-fn* -main)
