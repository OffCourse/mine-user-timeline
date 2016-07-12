(ns app.specs
  (:require [cljs.spec :as spec]
            [clojure.string :as str]))

(spec/def ::timestamp string?)
(spec/def ::user string?)
(spec/def ::url string?)
(spec/def ::min-id string?)

(spec/def ::user-data (spec/keys :req-un [::user]
                                 :opt-un [::min-id]))

(spec/def ::bookmark (spec/keys :req-un [::url ::user ::timestamp]))
(spec/def ::bookmarks (spec/* ::bookmark))

(spec/def ::payload (spec/or :bookmarks ::bookmarks
                             :user-data ::user-data))
(spec/def ::type string?)
(spec/def ::event (spec/keys :req-un [::payload ::type]))
