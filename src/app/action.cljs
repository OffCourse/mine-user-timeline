(ns app.action
  (:require [cljs.spec :as spec]
            [app.specs :as specs]
            [app.logger :as logger]
            [clojure.string :as str]))

(defn json->clj [data]
  (-> (.parse js/JSON data "ascii")
      (js->clj :keywordize-keys true)))

(defn buffer->clj [data]
  (-> data
      (js/Buffer "base64")
      (.toString "ascii")
      json->clj))

(defn extract-payload [records]
  (map #(-> %1 :kinesis :data buffer->clj) records))

(defn extract-event-source [record]
  (-> record
      :eventSourceARN
      (str/split "/")
      last))

(spec/fdef convert :ret ::specs/action)

(defn convert [event]
  (logger/log "Event: " event)
  (let [records (:Records (js->clj event :keywordize-keys true))
        payload (first (extract-payload records))
        event-source (extract-event-source (first records))
        action {:payload payload
                :type event-source}]
    (if (spec/valid? ::specs/action action)
      (do
        (logger/log "Incoming: " action)
        (spec/conform ::specs/action action))
      (logger/log-error :invalid-incoming-action action))))

(defmulti create (fn [payload] (first (spec/conform ::specs/payload payload))))

(defmethod create :tweets create [data]
  {:type "mined-tweets"
   :payload data})

(defmethod create :user-data create [data]
  {:type "requested-twitter-users"
   :payload [data]})

#_(spec/instrument #'convert)
