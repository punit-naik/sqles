(ns org.clojars.punit-naik.sqles.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [org.clojars.punit-naik.sqles.config :as config]
            [org.clojars.punit-naik.sqles.core :as core]))

(deftest init-test
  (testing "Init test, passing"
    (let [{:keys [status]
           {:keys [tagline]
            {:keys [number]} :version} :body}
          (core/run-query {:url (config/es-server)})]
      (is (pos-int? status))
      (if (= status 200)
        (is (re-matches #"\d+\.\d+\.\d+" number))
        (is (= tagline "You Know, for Search"))))))
