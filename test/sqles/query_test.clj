(ns sqles.query-test
  (:require [clojure.test :refer [deftest is]]
            [sqles.config :as config]
            [sqles.query :as query]))

(deftest select-test
  (is (= {:_source [:a]} (query/select "a")))
  (is (= {:_source [:a :b]} (query/select ["a" "b"])))
  (is (nil? (query/select "*")))
  (is (nil? (query/select ["*"]))))

(deftest from-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= "http://localhost:9200/test-1" (query/from "test-1")))
    (is (= "http://localhost:9200/test-2" (query/from "test-2")))))

(deftest op->op-key-test
  (is (->> (map #(let [op-key (query/op->op-key %)]
                   (condp = %
                     "=" (= op-key :equals)
                     "<" (= op-key :less-than)
                     "<=" (= op-key :less-than-or-equals)
                     ">=" (= op-key :greater-than-or-equals)
                     ">" (= op-key :greater-than)
                     "!=" (= op-key :not-equals)
                     false))
                query/operators-used-without-spacing)
           (every? true?))))

(deftest where-test
  (is (= {:term {:a.keyword 1}} (query/where "a" "=" 1)))
  (is (= {:range {:a {:lt 1}}} (query/where "a" "<" 1)))
  (is (= {:range {:a {:gt 1}}} (query/where "a" ">" 1)))
  (is (= {:range {:a {:lte 1}}} (query/where "a" "<=" 1)))
  (is (= {:range {:a {:gte 1}}} (query/where "a" ">=" 1)))
  (is (= {:term {:a.keyword 1}} (query/where "a" "!=" 1)))
  (is (= {:terms {:a.keyword [1 2]}} (query/where "a" "in" [1 2])))
  (is (= {:range {:a {:lte 1 :gte 3}}} (query/where "a" "between" [1 3]))))