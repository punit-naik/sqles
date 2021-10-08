(ns org.clojars.punit-naik.sqles.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [org.clojars.punit-naik.sqles.config :as config]
            [org.clojars.punit-naik.sqles.core :as core]))

(deftest run-query-test
  (testing "`run-query` fn tests"
    (let [{:keys [status]
           {:keys [tagline]
            {:keys [number]} :version} :body}
          (core/run-query {:url (config/es-server)})]
      (is (pos-int? status))
      (if (and (not (nil? status)) (= status 200))
        (is (re-matches #"\d+\.\d+\.\d+" number))
        (is (= tagline "You Know, for Search"))))
    (let [{:keys [status] {error-msg :error} :body} (core/run-query {:url "http://localhost:9200" :method :post})]
      (if (and (not (nil? status)) (= status 405))
        (is (= status 405))
        (is (= error-msg "Incorrect HTTP method for uri [/] and method [POST], allowed: [GET, HEAD, DELETE]"))))
    (is (= (core/run-query {:url "http://xyz.pqr"}) {}))
    (is (= (core/run-query {:url "http://localhost:9300"}) {}))
    (is (= (core/run-query {:url "http://localhost:9122"}) {}))
    (is (= (core/run-query {:url "http://localhost:9123123123"}) {}))))
