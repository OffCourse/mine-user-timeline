(ns app.twitter
  (:require [cljs.core.async :refer [<! put! close! chan >!]]
            [cljs.nodejs :as node])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def twitter (node/require "twit"))

(def twitter-config {:consumer_key (.. js/process -env -TWITTER_CONSUMER_KEY)
                     :consumer_secret (.. js/process -env -TWITTER_CONSUMER_SECRET)
                     :access_token (.. js/process -env -TWITTER_ACCESS_TOKEN)
                     :access_token_secret (.. js/process -env -TWITTER_ACCESS_TOKEN_SECRET)})

(def client (twitter. (clj->js twitter-config)))

(defn get-tweets [user-name max-id]
  (let [c (chan)
        params {:screen_name user-name
                :count 2}
        params (if max-id (assoc params :max_id max-id) params)]
    (.get client "statuses/user_timeline" (clj->js params) (fn [error tweets response]
                                                             (go (if error
                                                                   (println error)
                                                                   (>! c (js->clj tweets :keywordize-keys true))))))
    c))
