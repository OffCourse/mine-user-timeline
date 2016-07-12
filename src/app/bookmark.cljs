(ns app.bookmark)

(defmulti extract (fn [type _] type))

(defmethod extract :one [type tweet]
  {:user (-> tweet :user :screen_name)
   :url (-> tweet :entities :urls first :expanded_url)
   :timestamp (-> tweet :created_at)})

(defmethod extract :many [type tweets]
  (->> tweets
       (map #(extract :one %1))
       (filter #(:url %1))))
