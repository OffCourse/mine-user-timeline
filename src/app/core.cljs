(ns app.core
  (:require [cljs.nodejs :as node]
            [cljs.core.match :refer-macros [match]]
            [cljs.core.async :refer [<! put! close! chan >!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private AWS (node/require "aws-sdk"))
(def twitter (node/require "twit"))
(node/enable-util-print!)

(.. js/process -env -RETHINK_HOST)
(def Kinesis (new AWS.Kinesis))

(defn create-message [tweets]
  {:StreamName "tweeted-bookmarks"
   :Records (map (fn [tweet]
                   (let [tweet {:user (-> tweet :user :screen_name)
                                :url (-> tweet :entities :urls first :expanded_url)
                                :timestamp (-> tweet :created_at)}]
                     {:Data (.stringify js/JSON (clj->js tweet))
                      :PartitionKey "user"}))
                 tweets)})

(defn send-message [msg]
  (let [c (chan)]
    (.putRecords Kinesis (clj->js msg) #(if %1
                                          (println "error" %1)
                                          (go (>! c %2))))
    c))

(def twitter-config {:consumer_key (.. js/process -env -TWITTER_CONSUMER_KEY)
                     :consumer_secret (.. js/process -env -TWITTER_CONSUMER_SECRET)
                     :access_token (.. js/process -env -TWITTER_ACCESS_TOKEN)
                     :access_token_secret (.. js/process -env -TWITTER_ACCESS_TOKEN_SECRET)})

(def client (twitter. (clj->js twitter-config)))

(defn get-tweets [user-name max-id]
  (let [c (chan)
        params {:screen_name user-name
                :count 5}
        params (if max-id (assoc params :max_id max-id) params)]
    (.get client "statuses/user_timeline" (clj->js params) (fn [error tweets response]
                                                             (go (if error
                                                                   (println error)
                                                                   (>! c tweets)))))
    c))

(defn convert-payload [data]
  (-> js/JSON
      (.parse (.toString (js/Buffer. data "base64") "ascii"))
      (js->clj :keywordize-keys true)))

(defn extract-payload [event]
  (-> (:Records event)
      first
      first
      second
      :data))

(defn event->payload [event]
  (-> event
      (extract-payload)
      (convert-payload)
      (dissoc :id)))

(defn convert-event [event]
  (let [event (js->clj event :keywordize-keys true)
        payload (event->payload event)
        event-source (-> event
                         :Records
                         first
                         :eventSourceARN
                         (str/split "/")
                         last)]
    {:payload payload
     :event-source event-source}))


(defn ^:export handler [event context cb]
  (go
    (let [tweets (-> (<! (get-tweets "yeehaa" nil))
                     (js->clj :keywordize-keys true))
          message (create-message tweets)
          response (<! (send-message message))]
      (println (.stringify js/JSON (clj->js message)))
      (cb nil (clj->js response)))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
