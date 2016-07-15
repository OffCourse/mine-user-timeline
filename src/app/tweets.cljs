(ns app.tweets
  (:require [cljs.core.async :refer [<! put! close! chan >!]]
            [cljs.nodejs :as node])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def twitter (node/require "twit"))

(def twitter-config {:consumer_key (.. js/process -env -TWITTER_CONSUMER_KEY)
                     :consumer_secret (.. js/process -env -TWITTER_CONSUMER_SECRET)
                     :access_token (.. js/process -env -TWITTER_ACCESS_TOKEN)
                     :access_token_secret (.. js/process -env -TWITTER_ACCESS_TOKEN_SECRET)})

(def client (twitter. (clj->js twitter-config)))

(defn process [tweets user-name old-min-id]
  (let [new-min-id (or (apply min (map :id tweets)) 0)]
    {:tweets      tweets
     :user        user-name
     :min-id      new-min-id
     :has-tweets? (> (count tweets) 0)
     :has-more?   (or (not old-min-id) (> old-min-id new-min-id))}))

(defn fetch [{:keys [user min-id]}]
  (let [c      (chan)
        params {:screen_name user
                :count      5}
        params (if min-id (assoc params :max_id min-id) params)]
    (.get client
          "statuses/user_timeline"
          (clj->js params)
          (fn [error tweets response]
            (go (if error (println error)
                    (let [tweets (js->clj tweets :keywordize-keys true)]
                      (>! c (process tweets user min-id)))))))
    c))
