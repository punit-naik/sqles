(ns sqles.query-test
  (:require [clojure.test :refer [deftest is]]
            [sqles.config :as config]
            [sqles.query :as query]
            [sqles.parse-sql.where :as where]))

(deftest remove-quotes-test
  (is (= "Bob" (query/remove-quotes "\"Bob\"")))
  (is (= "Alice" (query/remove-quotes "'Alice'"))))

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
                where/operators-used-without-spacing)
           (every? true?))))

(deftest where-test
  (is (= {:range {:a {:lte 1, :gte 1}}} (query/where "a" "=" "1")))
  (is (= {:term {:a.keyword "test"}} (query/where "a" "=" "test")))
  (is (= {:range {:a {:lt 1}}} (query/where "a" "<" "1")))
  (is (= {:range {:a {:gt 1}}} (query/where "a" ">" "1")))
  (is (= {:range {:a {:lte 1}}} (query/where "a" "<=" "1")))
  (is (= {:range {:a {:gte 1}}} (query/where "a" ">=" "1")))
  (is (= {:term {:a.keyword "test"}} (query/where "a" "!=" "test")))
  (is (= {:terms {:a.keyword [1 2]}} (query/where "a" "in" "(1, 2)")))
  (is (= {:range {:a {:gte 1 :lte 3}}} (query/where "a" "between" "(1, 3)"))))

(deftest where->es-test
  (is (= (query/where->es
          {:and {:true [["a" "=" "1"]
                        ["b" "=" "2"]]
                 :false [["d" "!=" "4"]]}
           :or {:true [["c" "=" "3"]] :false [["e" "!=" "5"]]}})
         {:query
          {:bool
           {:must [{:range {:a {:lte 1, :gte 1}}}
                   {:range {:b {:lte 2, :gte 2}}}]
            :should [{:range {:c {:lte 3, :gte 3}}}
                     {:bool {:must_not {:range {:e {:lte 5, :gte 5}}}}}]
            :must_not [{:range {:d {:lte 4, :gte 4}}}]}}}))
  (is (= (query/where->es
          {:and
           {:true [["a" "=" "1"]
                   {:and {:true [], :false []}
                    :or {:true [], :false [["b" "!=" "2"] ["c" "!=" "3"]]}}]
            :false []}
           :or {:true [], :false []}})
         {:query
          {:bool
           {:must [{:range {:a {:lte 1, :gte 1}}}
                   {:bool
                    {:should [{:bool {:must_not {:range {:b {:lte 2, :gte 2}}}}}
                              {:bool {:must_not {:range {:c {:lte 3, :gte 3}}}}}]}}]}}})))